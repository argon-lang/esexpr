// Closed hierarchy APIs are experimental in the .NET 11 RC2 Roslyn release.
#pragma warning disable RSEXPERIMENTAL006

using System;
using System.Collections.Generic;
using System.Linq;
using System.Numerics;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using static ESExpr.SourceGenerator.GenUtils;

namespace ESExpr.SourceGenerator;

internal static class SourceModelValidation {
	private static readonly string[] InlineFieldAttributes = ["Keyword", "Optional", "Vararg", "Dict"];
	public static void ValidateSyntax(BaseTypeDeclarationSyntax decl, SemanticModel semantic, GenerationDiagnostics diagnostics) {
		if(decl is not RecordDeclarationSyntax type) return;
		var codecAttribute = GetAttribute(type, "ESExpr.Runtime.ESExprCodecAttribute", semantic);
		if(codecAttribute?.ArgumentList?.Arguments.Any(a => a.NameEquals?.Name.ToString() == "Flags" && a.Expression is LiteralExpressionSyntax { Token.Value: true }) == true) return;
		void Fields(TypeDeclarationSyntax owner) {
			var keywords = new HashSet<string>();
			bool hasDict = false;
			foreach(var prop in owner.Members.OfType<PropertyDeclarationSyntax>().Where(p => !p.Modifiers.Any(SyntaxKind.StaticKeyword))) {
				var location = prop.Identifier.GetLocation();
				var optional = HasAttribute(prop, "ESExpr.Runtime.OptionalAttribute", semantic);
				var dict = !optional && HasAttribute(prop, "ESExpr.Runtime.DictAttribute", semantic);
				var vararg = !optional && !dict && HasAttribute(prop, "ESExpr.Runtime.VarargAttribute", semantic);
				var keyword = GetAttribute(prop, "ESExpr.Runtime.KeywordAttribute", semantic);
				if(dict) {
					if(hasDict) diagnostics.Report(Diagnostic.Create(Errors.MultipleDict, location));
					hasDict = true;
				}
				else if(!vararg && keyword is not null) {
					if(hasDict) diagnostics.Report(Diagnostic.Create(Errors.KeywordWithDict, location));
					string? name = keyword.ArgumentList?.Arguments.Count == 1
						? (keyword.ArgumentList.Arguments[0].Expression as LiteralExpressionSyntax)?.Token.Value as string
						: NameUtils.NameToKebabCase(prop.Identifier.Text);
					if(name is null) diagnostics.Report(Diagnostic.Create(Errors.MalformedMetadata, location, "KeywordAttribute must have a string literal argument"));
					if(name is not null && !keywords.Add(name)) diagnostics.Report(Diagnostic.Create(Errors.DuplicateKeyword, location, name));
				}
			}
		}
		if(type.Modifiers.Any(SyntaxKind.SealedKeyword)) {
			Fields(type);
			return;
		}
		if(!type.Modifiers.Any(SyntaxKind.ClosedKeyword)) return;
		foreach(var c in type.Members.OfType<RecordDeclarationSyntax>().Where(c => c.Modifiers.Any(SyntaxKind.PublicKeyword))) {
			Fields(c);
			if(!HasAttribute(c, "ESExpr.Runtime.InlineValueAttribute", semantic)) continue;
			var fields = c.Members.OfType<PropertyDeclarationSyntax>().Where(p => !p.Modifiers.Any(SyntaxKind.StaticKeyword)).ToList();
			if(fields.Count != 1) diagnostics.Report(Diagnostic.Create(Errors.InvalidInlineValue, c.Identifier.GetLocation()));
			else if(InlineFieldAttributes.Any(a => HasAttribute(fields[0], "ESExpr.Runtime." + a + "Attribute", semantic)))
				diagnostics.Report(Diagnostic.Create(Errors.InvalidInlineDeclaration, fields[0].Identifier.GetLocation(), "Inline value field must be a required positional argument"));
		}
	}

	public static void Validate(ITypeSourceModel? model, GenerationDiagnostics diagnostics) {
		void Fields(IEnumerable<SourceModelField> fields) {
			var previous = ESExprTagSet.Empty;
			Location? last = null;
			foreach(var field in fields) {
				// A failed intervening property may consume positional arguments; its tags are unknown.
				if(last is not null && diagnostics.FailedFields.Any(l => l.SourceTree == field.Location.SourceTree && l.SourceSpan.Start > last.SourceSpan.Start && l.SourceSpan.Start < field.Location.SourceSpan.Start)) previous = ESExprTagSet.Empty;
				last = field.Location;
				ESExprTagSet? tags = field.Mode switch {
					SourceModelField.FieldMode.Normal { KeywordMode: SourceModelField.KeywordMode.Positional p } => p.Tags,
					SourceModelField.FieldMode.Optional { KeywordMode: SourceModelField.KeywordMode.Positional p } => p.Tags,
					SourceModelField.FieldMode.Vararg v => v.ElementTags,
					_ => null,
				};
				if(tags is null) continue;
				if(!previous.IsDisjointFrom(tags)) diagnostics.Report(Diagnostic.Create(Errors.OverlappingFieldTags, field.Location, field.Name, tags, previous));
				previous = field.Mode switch {
					SourceModelField.FieldMode.Normal { DefaultValue: null } => ESExprTagSet.Empty,
					_ => previous.Union(tags),
				};
			}
		}
		switch(model) {
			case InvalidTypeSourceModel invalid:
				diagnostics.Report(Diagnostic.Create(invalid.Descriptor, invalid.Location, invalid.MessageArgs.Cast<object>().ToArray()));
				break;
			case RecordSourceModel record: Fields(record.Fields); break;
			case UnionRecordSourceModel union: {
				var previous = ESExprTagSet.Empty;
				foreach(var c in union.Cases) {
					Fields(c.Fields);
					ESExprTagSet? tags = c.IsInlineValue
						? !diagnostics.Diagnostics.Any(d => d.Id == Errors.InvalidInlineValue.Id && d.Location.Equals(c.Location)) && c.Fields.Count == 1 && c.Fields[0].Mode is SourceModelField.FieldMode.Normal { KeywordMode: SourceModelField.KeywordMode.Positional p } ? p.Tags : null
						: c.ConstructorName is null ? null : ESExprTagSet.Create([new ESExprTag.Constructor(c.ConstructorName)]);
					if(tags is null) continue;
					if(!previous.IsDisjointFrom(tags)) diagnostics.Report(Diagnostic.Create(Errors.OverlappingEnumConstructors, c.Location, union.TypeName, tags, previous));
					previous = previous.Union(tags);
				}
				break;
			}
			case SimpleEnumSourceModel simple: {
				var names = new HashSet<string>();
				foreach(var c in simple.Cases) if(c.ConstructorName is not null && !names.Add(c.ConstructorName)) diagnostics.Report(Diagnostic.Create(Errors.OverlappingEnumConstructors, c.Location, simple.TypeName, c.ConstructorName, string.Join(", ", names)));
				break;
			}
			case FlagsSourceModel flags: {
				BigInteger used = 0;
				foreach(var field in flags.Fields) {
					var mask = field switch {
						SourceModelFlagsFieldFlag flag => flag.Mask,
						SourceModelFlagsFieldEnum e => e.Cases.Aggregate(BigInteger.Zero, (m, c) => m | c.Value),
					};
					if(field is SourceModelFlagsFieldFlag && (mask <= 0 || (mask & (mask - 1)) != 0))
						diagnostics.Report(Diagnostic.Create(Errors.InvalidFlag, field.Location, mask == 0 ? "Flag mask must have at least one bit set" : "Flag mask must have only one bit set"));
					if((used & mask) != 0) diagnostics.Report(Diagnostic.Create(Errors.OverlappingFlagMasks, field.Location, field is SourceModelFlagsFieldFlag ? "Duplicate flag mask" : "Duplicate enum mask"));
					used |= mask;
				}
				break;
			}
		}
	}
}
