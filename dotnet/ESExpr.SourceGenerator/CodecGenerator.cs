using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;
using System.Linq.Expressions;
using System.Text;
using ESExpr.SourceGenerator.TypeClass;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using static Microsoft.CodeAnalysis.CSharp.SyntaxFactory;

namespace ESExpr.SourceGenerator;

internal abstract class CodecGenerator<TTypeModel> : ICodecGenerator where TTypeModel : TypeSourceModelDeclaration {

	public required SourceProductionContext Context { get; init; }

	public required TTypeModel TypeModel { get; init; }

	protected abstract BlockSyntax GenerateIsEqualBody();
	protected abstract BlockSyntax GenerateEncodeBody();
	protected abstract BlockSyntax GenerateDecodeBody();


	public void Generate() {
		var syntaxTree = CompilationUnit()
			.AddUsings(TypeModel.Usings.Select(u => u.Syntax).ToArray());

		TypeSyntax outerType = TypeModel.TypeParameters.IsEmpty
			? IdentifierName(TypeModel.TypeName)
			: GenericName(Identifier(TypeModel.TypeName))
				.AddTypeArgumentListArguments(
					TypeModel.TypeParameters
						.Select(tp => IdentifierName(tp.Syntax.Identifier.Text))
						.ToArray<TypeSyntax>()
				);

		var members = new List<MemberDeclarationSyntax>();

		MemberDeclarationSyntax typeClassInstanceMember;
		if(TypeModel.TypeClassInstanceAccessor.IsProperty) {
			typeClassInstanceMember =
				PropertyDeclaration(
					ESExprCodecType(outerType),
					TypeModel.TypeClassInstanceAccessor.Name
				)
				.AddAttributeLists(AttributeList(SeparatedList([
					TagsToAttribute(TypeModel.Tags),
				])))
				.AddModifiers(
					Token(SyntaxKind.PublicKeyword),
					Token(SyntaxKind.StaticKeyword),
					Token(SyntaxKind.PartialKeyword)
				)
				.WithExpressionBody(ArrowExpressionClause(
					ObjectCreationExpression(IdentifierName("GeneratedCodecImpl"))
						.WithArgumentList(ArgumentList([]))
				))
				.WithSemicolonToken(Token(SyntaxKind.SemicolonToken));
		}
		else {
			typeClassInstanceMember =
				MethodDeclaration(
					ESExprCodecType(outerType),
					TypeModel.TypeClassInstanceAccessor.Name
				)
				.AddAttributeLists(AttributeList(SeparatedList([
					TagsToAttribute(TypeModel.Tags),
				])))
				.AddModifiers(
					Token(SyntaxKind.PublicKeyword),
					Token(SyntaxKind.StaticKeyword),
					Token(SyntaxKind.PartialKeyword)
				)
				.AddTypeParameterListParameters(
					TypeModel.TypeParameters.Select(tp => tp.Syntax).ToArray()
				)
				.AddParameterListParameters(
					TypeModel.TypeClassInstanceAccessor.Parameters
						.Select(p =>
							Parameter(Identifier(p.Name))
								.WithType(ConvertTypeToTypeSyntax(p.Type))
						)
						.ToArray()
				)
				.WithExpressionBody(ArrowExpressionClause(
					ObjectCreationExpression(
						TypeModel.TypeParameters.IsEmpty
							? IdentifierName("GeneratedCodecImpl")
							: GenericName(Identifier("GeneratedCodecImpl"))
								.AddTypeArgumentListArguments(
									TypeModel.TypeParameters
										.Select(tp => IdentifierName(tp.Syntax.Identifier.Text))
										.ToArray<TypeSyntax>()
								)
					)
						.AddArgumentListArguments(
							TypeModel.TypeClassInstanceAccessor.Parameters
								.Select(p => Argument(IdentifierName(p.Name)))
								.ToArray()
						)
				))
				.WithSemicolonToken(Token(SyntaxKind.SemicolonToken));
		}
		
		

		
		var constructor = ConstructorDeclaration("GeneratedCodecImpl")
			.AddModifiers(Token(SyntaxKind.PublicKeyword))
			.AddParameterListParameters(
				TypeModel.TypeClassInstanceAccessor.Parameters
					.Select(p =>
						Parameter(Identifier("instance_" + p.Name))
							.WithType(ConvertTypeToTypeSyntax(p.Type))
					)
					.ToArray()
			)
			.WithBody(Block(List(
				TypeModel.TypeClassInstanceAccessor.Parameters.Select(p => {
					var fieldName = "instance_" + p.Name;

					return ExpressionStatement(AssignmentExpression(
						SyntaxKind.SimpleAssignmentExpression,
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							ThisExpression(),
							IdentifierName(fieldName)
						),
						IdentifierName(fieldName)
					));
				})
			)));

		members.Add(constructor);

		foreach(var p in TypeModel.TypeClassInstanceAccessor.Parameters) {
			members.Add(
				FieldDeclaration(
						VariableDeclaration(ConvertTypeToTypeSyntax(p.Type))
							.WithVariables(SeparatedList(new VariableDeclaratorSyntax[] {
								VariableDeclarator(Identifier("instance_" + p.Name)),
							}))
					)
					.AddModifiers(Token(SyntaxKind.PrivateKeyword), Token(SyntaxKind.ReadOnlyKeyword))
			);
		}
		
		var tagsProp =
			PropertyDeclaration(
				QualifiedName(
					QualifiedName(
						AliasQualifiedName(
							IdentifierName(Token(SyntaxKind.GlobalKeyword)),
							IdentifierName("ESExpr")),
						IdentifierName("Runtime")
					),
					IdentifierName("ESExprTagSet")
				),
				Identifier("Tags")
			)
			.AddModifiers(Token(SyntaxKind.PublicKeyword))
			.WithExpressionBody(
				ArrowExpressionClause(
					WriteTagsExpr()
				)
			)
			.WithSemicolonToken(Token(SyntaxKind.SemicolonToken));
		members.Add(tagsProp);

		var isEncodedEqualMethod =
			MethodDeclaration(
				PredefinedType(Token(SyntaxKind.BoolKeyword)),
				Identifier("IsEncodedEqual")
			)
			.AddModifiers(Token(SyntaxKind.PublicKeyword))
			.AddParameterListParameters(
				Parameter(Identifier("a")).WithType(outerType),
				Parameter(Identifier("b")).WithType(outerType)
			)
			.WithBody(GenerateIsEqualBody());
		members.Add(isEncodedEqualMethod);
		
		var encodeMethod =
			MethodDeclaration(
					ESExprType,
					Identifier("Encode")
				)
				.AddModifiers(Token(SyntaxKind.PublicKeyword))
				.AddParameterListParameters(
					Parameter(Identifier("value")).WithType(outerType)
				)
				.WithBody(GenerateEncodeBody());
		members.Add(encodeMethod);

		var decodeMethod =
			MethodDeclaration(
					outerType,
					Identifier("Decode")
				)
				.AddModifiers(Token(SyntaxKind.PublicKeyword))
				.AddParameterListParameters(
					Parameter(Identifier("expr")).WithType(ESExprType),
					Parameter(Identifier("path")).WithType(DecodeFailurePathType)
				)
				.WithBody(GenerateDecodeBody());
		members.Add(decodeMethod);

		var codecClass = ClassDeclaration("GeneratedCodecImpl")
			.WithBaseList(BaseList(
				Token(SyntaxKind.ColonToken),
				SeparatedList(new BaseTypeSyntax[] {
					SimpleBaseType(ESExprCodecType(outerType)),
				})
			))
			.AddModifiers(Token(SyntaxKind.PrivateKeyword), Token(SyntaxKind.SealedKeyword))
			.AddMembers(members.ToArray());

		// Use a static class if the original type is generic
		var outerClass = RecordDeclaration(
			Token(TypeModel.TypeParameters.Count == 0 ? SyntaxKind.RecordKeyword : SyntaxKind.ClassKeyword),
			TypeModel.TypeName
		);
		
		if(TypeModel.TypeParameters.Count != 0) {
			outerClass = outerClass.AddModifiers(Token(SyntaxKind.StaticKeyword));
			
			codecClass = codecClass.AddTypeParameterListParameters(TypeModel.TypeParameters.Select(tp => tp.Syntax).ToArray());
		}
		
		outerClass = outerClass			
			.AddModifiers(Token(SyntaxKind.PartialKeyword))
			.WithOpenBraceToken(Token(SyntaxKind.OpenBraceToken))
			.WithCloseBraceToken(Token(SyntaxKind.CloseBraceToken))
			.AddMembers(typeClassInstanceMember, codecClass);
		
		// Generate the namespace with the class
		var nsName = GetNamespaceName();

		string fileNamePrefix;
		if(nsName is not null) {
			var namespaceDeclaration = NamespaceDeclaration(nsName)
				.AddMembers(outerClass);

			syntaxTree = syntaxTree.AddMembers(namespaceDeclaration);
			fileNamePrefix = nsName.ToString() + "." + TypeModel.TypeName;
		}
		else {
			syntaxTree = syntaxTree.AddMembers(outerClass);
			fileNamePrefix = TypeModel.TypeName;
		}

		syntaxTree = syntaxTree.NormalizeWhitespace();



		Context.AddSource($"{fileNamePrefix}.ESExprCodec.g.cs", syntaxTree.GetText(Encoding.UTF8));
	}

	private ExpressionSyntax WriteTagsExpr() =>
		TagsToExpr(TypeModel.Tags);
	

	private ExpressionSyntax TagsToExpr(ESExprTagSet tags) {
		return tags.Visit<ExpressionSyntax>(
			visitAll: () => MemberAccessExpression(
				SyntaxKind.SimpleMemberAccessExpression,
				ESExprTagSetType,
				IdentifierName("All")
			),
			visitFinite: tags => 
				InvocationExpression(
					MemberAccessExpression(
						SyntaxKind.SimpleMemberAccessExpression,
						ESExprTagSetType,
						IdentifierName("Create")
					),
					ArgumentList(
						SingletonSeparatedList(
							Argument(
								CollectionExpression(
									SeparatedList<CollectionElementSyntax>(
										tags.Select(tag => tag switch {
											ESExprTag.Constructor(var constructor) => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Constructor))
												),
												ArgumentList([
													Argument(
														LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(constructor))
													),
												]),
												null
											),
											ESExprTag.Bool => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Bool))
												),
												ArgumentList([]),
												null
											),
											ESExprTag.Int => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Int))
												),
												ArgumentList([]),
												null
											),
											ESExprTag.Str => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Str))
												),
												ArgumentList([]),
												null
											),
											ESExprTag.Float16 => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Float16))
												),
												ArgumentList([]),
												null
											),
											ESExprTag.Float32 => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Float32))
												),
												ArgumentList([]),
												null
											),
											ESExprTag.Float64 => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Float64))
												),
												ArgumentList([]),
												null
											),
											ESExprTag.Array8 => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Array8))
												),
												ArgumentList([]),
												null
											),
											ESExprTag.Array16 => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Array16))
												),
												ArgumentList([]),
												null
											),
											ESExprTag.Array32 => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Array32))
												),
												ArgumentList([]),
												null
											),
											ESExprTag.Array64 => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Array64))
												),
												ArgumentList([]),
												null
											),
											ESExprTag.Array128 => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Array128))
												),
												ArgumentList([]),
												null
											),
											ESExprTag.Null => ObjectCreationExpression(
												QualifiedName(
													ESExprTagType,
													IdentifierName(nameof(ESExprTag.Null))
												),
												ArgumentList([]),
												null
											),
											
											_ => throw new Exception("Unknown tag type: " + tag.GetType()),
										}).Select(ExpressionElement)
									)
								)
							)
						)
					)
				)
		);

	}
	
	private AttributeSyntax TagsToAttribute(ESExprTagSet tags) {
		var attrName = QualifiedName(
			QualifiedName(
				AliasQualifiedName(
					IdentifierName(Token(SyntaxKind.GlobalKeyword)),
					IdentifierName("ESExpr")
				),
				IdentifierName("Runtime")
			),
			IdentifierName("ESExprTags")
		);
		
		return tags.Visit<AttributeSyntax>(
			visitAll: () => Attribute(
				attrName,
				AttributeArgumentList([
					AttributeArgument(
						NameEquals("All"),
						null,
						LiteralExpression(SyntaxKind.TrueLiteralExpression)
					)
				])
			),
			visitFinite: tags => Attribute(
				attrName,
				AttributeArgumentList([
					AttributeArgument(
						NameEquals("Scalar"),
						null,
						CollectionExpression(SeparatedList<CollectionElementSyntax>(
							tags
								.Select<ESExprTag, ESExprTag.ScalarType?>(t => t switch {
									ESExprTag.Bool => ESExprTag.ScalarType.Bool,
									ESExprTag.Int => ESExprTag.ScalarType.Int,
									ESExprTag.Str => ESExprTag.ScalarType.Str,
									ESExprTag.Float16 => ESExprTag.ScalarType.Float16,
									ESExprTag.Float32 => ESExprTag.ScalarType.Float32,
									ESExprTag.Float64 => ESExprTag.ScalarType.Float64,
									ESExprTag.Array8 => ESExprTag.ScalarType.Array8,
									ESExprTag.Array16 => ESExprTag.ScalarType.Array16,
									ESExprTag.Array32 => ESExprTag.ScalarType.Array32,
									ESExprTag.Array64 => ESExprTag.ScalarType.Array64,
									ESExprTag.Array128 => ESExprTag.ScalarType.Array128,
									ESExprTag.Null => ESExprTag.ScalarType.Null,
									_ => null
								})
								.Where(t => t != null)
								.Select(tNull => {
									var t = tNull!.Value;
									
									return ExpressionElement(
										QualifiedName(
											QualifiedName(
												QualifiedName(
													QualifiedName(
														AliasQualifiedName(
															IdentifierName(Token(SyntaxKind.GlobalKeyword)),
															IdentifierName("ESExpr")
														),
														IdentifierName("Runtime")
													),
													IdentifierName("ESExprTag")
												),
												IdentifierName("ScalarType")
											),
											IdentifierName(t.ToString())
										)
									);
								})
							
						))
					),
					AttributeArgument(
						NameEquals("Constructors"),
						null,
						CollectionExpression(SeparatedList<CollectionElementSyntax>(
							tags
								.OfType<ESExprTag.Constructor>()
								.Select(c => ExpressionElement(LiteralExpression(
									SyntaxKind.StringLiteralExpression,
									Literal(c.constructor)
								)))
						))
					),
				])
			)
				
		);

	}
	
	protected BlockSyntax WriteIsEqualFields(IReadOnlyList<SourceModelField> fields, ExpressionSyntax aExpr, ExpressionSyntax bExpr) {
		var stmts = new List<StatementSyntax>();

		foreach(var field in fields) {
			ExpressionSyntax memberAccessA = MemberAccessExpression(
				SyntaxKind.SimpleMemberAccessExpression,
				aExpr,
				IdentifierName(field.Name)
			);
			ExpressionSyntax memberAccessB = MemberAccessExpression(
				SyntaxKind.SimpleMemberAccessExpression,
				bExpr,
				IdentifierName(field.Name)
			);

			
			ExpressionSyntax codecExpr = field.Mode switch {
				SourceModelField.FieldMode.Normal { CodecInstance: var codec } => GetCodecExpr(codec),
				SourceModelField.FieldMode.Optional { OptionalValueCodecInstance: var codec } => GetCodecExpr(codec),
				SourceModelField.FieldMode.Dict { DictCodecInstance: var codec } => GetCodecExpr(codec),
				SourceModelField.FieldMode.Vararg { VarargCodecInstance: var codec } => GetCodecExpr(codec),
				_ => throw new ArgumentOutOfRangeException()
			};

			var isEqualCall = InvocationExpression(
				MemberAccessExpression(
					SyntaxKind.SimpleMemberAccessExpression,
					codecExpr,
					IdentifierName("IsEncodedEqual")
				),
				ArgumentList(SeparatedList([
					Argument(memberAccessA),
					Argument(memberAccessB),
				]))
			);

			stmts.Add(IfStatement(
				PrefixUnaryExpression(
					SyntaxKind.LogicalNotExpression,
					isEqualCall
				),
				Block(ReturnStatement(
					LiteralExpression(SyntaxKind.FalseLiteralExpression)
				))
			));
		}

		stmts.Add(ReturnStatement(
			LiteralExpression(SyntaxKind.TrueLiteralExpression)
		));

		return Block(stmts);
	}

	protected BlockSyntax WriteEncodeFields(string constructorName, IReadOnlyList<SourceModelField> fields, ExpressionSyntax valueExpr) {
		var argsDeclaration = ParseStatement(
			"var args = global::System.Collections.Immutable.ImmutableList.CreateBuilder<global::ESExpr.Runtime.Expr>();"
		);

		var kwargsDeclaration = ParseStatement(
			"var kwargs = global::System.Collections.Immutable.ImmutableDictionary.CreateBuilder<string, global::ESExpr.Runtime.Expr>();"
		);
		
		// return new global::ESExpr.Runtime.Expr.Constructor(name, args.ToImmutable(), kwargs.ToImmutable());
		var returnStatement = ReturnStatement(
			ObjectCreationExpression(
					QualifiedName(
							ESExprType,
							IdentifierName("Constructor")
						)
				)
				.WithArgumentList(
					ArgumentList(
						SeparatedList([
							Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(constructorName))),
							Argument(
								InvocationExpression(
									QualifiedName(IdentifierName("args"), IdentifierName("ToImmutable"))
								)
							),
							Argument(
								InvocationExpression(
									QualifiedName(IdentifierName("kwargs"), IdentifierName("ToImmutable"))
								)
							),
						])
					)
				)
		);

		var stmts = new List<StatementSyntax> {
			argsDeclaration,
			kwargsDeclaration,
		};

		bool hasDict = false;
		var prevOptionalPositionalTags = ESExprTagSet.Empty;
		var keywords = new HashSet<string>();

		foreach(var field in fields) {
			var propertyValue = MemberAccessExpression(
				SyntaxKind.SimpleMemberAccessExpression,
				valueExpr,
				IdentifierName(field.Name)
			);
			
			void PosTagCheck(ESExprTagSet tags) {
				if(prevOptionalPositionalTags.IsEmpty) {
					return;
				}

				if(!prevOptionalPositionalTags.IsDisjointFrom(tags)) {
					Context.ReportDiagnostic(Diagnostic.Create(
						Errors.OverlappingFieldTags,
						field.Location,
						field.Name,
						tags,
						prevOptionalPositionalTags
					));
				}
			}

			switch(field.Mode) {
				case SourceModelField.FieldMode.Normal {
					DefaultValue: null,
					CodecInstance: var codec,
					KeywordMode: SourceModelField.KeywordMode.Positional { Tags: var tags }
				} normalField: {
					if(!prevOptionalPositionalTags.IsEmpty) {
						PosTagCheck(tags);
						prevOptionalPositionalTags = ESExprTagSet.Empty;
					}
					
					var encodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(codec),
							IdentifierName("Encode")
						),
						ArgumentList(SeparatedList([
							Argument(propertyValue),
						]))
					);

					var expr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							IdentifierName("args"),
							IdentifierName("Add")
						),
						ArgumentList(SeparatedList([
							Argument(encodedExpr),
						]))
					);

					stmts.Add(ExpressionStatement(expr));
					
					break;
				}
				
				case SourceModelField.FieldMode.Normal {
					DefaultValue: {} defaultValue,
					CodecInstance: var codec,
					KeywordMode: SourceModelField.KeywordMode.Positional { Tags: var tags }
				} normalField: {
					PosTagCheck(tags);
					prevOptionalPositionalTags = prevOptionalPositionalTags.Union(tags);
					
					var codecExpr = GetCodecExpr(codec);

					stmts.Add(Block(
						LocalDeclarationStatement(
							VariableDeclaration(ESExprType)
								.WithVariables(
									SingletonSeparatedList(
										VariableDeclarator(Identifier("encodedExpr"))
											.WithInitializer(
												EqualsValueClause(
													InvocationExpression(
														MemberAccessExpression(
															SyntaxKind.SimpleMemberAccessExpression,
															codecExpr,
															IdentifierName("Encode")
														),
														ArgumentList(SeparatedList([
															Argument(propertyValue),
														]))
													)
												)
											)
									)
								)
						),

						IfStatement(
							PrefixUnaryExpression(
								SyntaxKind.LogicalNotExpression,
								InvocationExpression(
									MemberAccessExpression(
										SyntaxKind.SimpleMemberAccessExpression,
										codecExpr,
										IdentifierName("IsEncodedEqual")
									),
									ArgumentList(SeparatedList([
										Argument(propertyValue),
										Argument(defaultValue.Syntax),
									]))
								)
							),
							Block(
								ExpressionStatement(
									InvocationExpression(
										MemberAccessExpression(
											SyntaxKind.SimpleMemberAccessExpression,
											IdentifierName("args"),
											IdentifierName("Add")
										),
										ArgumentList(SeparatedList([
											Argument(IdentifierName("encodedExpr")),
										]))
									)
								)
							)
						)
					));
					
					break;
				}
				
				case SourceModelField.FieldMode.Normal {
					CodecInstance: var codec,
					KeywordMode: SourceModelField.KeywordMode.Keyword { KeywordName: var keyword }
				} normalField: {
					if(hasDict) {
						Context.ReportDiagnostic(Diagnostic.Create(
							Errors.KeywordWithDict,
							field.Location
						));
					}

					if(!keywords.Add(keyword)) {
						Context.ReportDiagnostic(Diagnostic.Create(
							Errors.DuplicateKeyword,
							field.Location,
							keyword
						));
					}
					
					
					var encodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(codec),
							IdentifierName("Encode")
						),
						ArgumentList(SeparatedList([
							Argument(propertyValue),
						]))
					);

					if(normalField.DefaultValue is {} defaultValue) {
						stmts.Add(Block(
							LocalDeclarationStatement(
								VariableDeclaration(ESExprType)
									.WithVariables(
										SingletonSeparatedList(
											VariableDeclarator(Identifier("encodedExpr"))
												.WithInitializer(
													EqualsValueClause(encodedExpr)
												)
										)
									)
							),

							IfStatement(
								PrefixUnaryExpression(
									SyntaxKind.LogicalNotExpression,
									InvocationExpression(
										MemberAccessExpression(
											SyntaxKind.SimpleMemberAccessExpression,
											GetCodecExpr(codec),
											IdentifierName("IsEncodedEqual")
										),
										ArgumentList(SeparatedList([
											Argument(propertyValue),
											Argument(defaultValue.Syntax),
										]))
									)
								),
								Block(
									ExpressionStatement(InvocationExpression(
										MemberAccessExpression(
											SyntaxKind.SimpleMemberAccessExpression,
											IdentifierName("kwargs"),
											IdentifierName("Add")
										),
										ArgumentList(SeparatedList(new[] {
											Argument(
												LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(keyword))
											),
											Argument(
												IdentifierName("encodedExpr")
											),
										}))
									))
								)
							)
						));
					}
					else {
						stmts.Add(ExpressionStatement(InvocationExpression(
							MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								IdentifierName("kwargs"),
								IdentifierName("Add")
							),
							ArgumentList(SeparatedList(new[] {
								Argument(
									LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(keyword))
								),
								Argument(encodedExpr),
							}))
						)));
					}
					
					break;
				}

				case SourceModelField.FieldMode.Optional {
					OptionalValueCodecInstance: var codec,
					ElementType: var elementType,
					KeywordMode: SourceModelField.KeywordMode.Positional { Tags: var tags },
				}: {
					PosTagCheck(tags);
					prevOptionalPositionalTags = prevOptionalPositionalTags.Union(tags);
					
					var encodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(codec),
							IdentifierName("EncodeOptional")
						),
						ArgumentList(SeparatedList([
							Argument(propertyValue),
						]))
					);


					var condition = IsPatternExpression(
						encodedExpr,
						RecursivePattern()
							.WithDesignation(SingleVariableDesignation(Identifier("encodedExpr")))
							.WithPropertyPatternClause(PropertyPatternClause(SeparatedList<SubpatternSyntax>()))
					);

					var ifStatement = IfStatement(
						condition,
						Block(
							ExpressionStatement(InvocationExpression(
								MemberAccessExpression(
									SyntaxKind.SimpleMemberAccessExpression,
									IdentifierName("args"),
									IdentifierName("Add")
								),
								ArgumentList(SeparatedList(new[] {
									Argument(
										IdentifierName("encodedExpr")
									),
								}))
							))
						)
					);

					stmts.Add(Block(
						ifStatement
					));
					
					break;
				}
				
				case SourceModelField.FieldMode.Optional {
					 OptionalValueCodecInstance: var codec,
					KeywordMode: SourceModelField.KeywordMode.Keyword { KeywordName: var keyword }
				}: {
					
					if(hasDict) {
						Context.ReportDiagnostic(Diagnostic.Create(
							Errors.KeywordWithDict,
							field.Location
						));
					}

					if(!keywords.Add(keyword)) {
						Context.ReportDiagnostic(Diagnostic.Create(
							Errors.DuplicateKeyword,
							field.Location,
							keyword
						));
					}
					
					
					var encodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(codec),
							IdentifierName("EncodeOptional")
						),
						ArgumentList(SeparatedList([
							Argument(propertyValue),
						]))
					);


					var condition = IsPatternExpression(
						encodedExpr,
						RecursivePattern()
							.WithDesignation(SingleVariableDesignation(Identifier("encodedExpr")))
							.WithPropertyPatternClause(PropertyPatternClause(SeparatedList<SubpatternSyntax>()))
					);

					var ifStatement = IfStatement(
						condition,
						Block(
							ExpressionStatement(InvocationExpression(
								MemberAccessExpression(
									SyntaxKind.SimpleMemberAccessExpression,
									IdentifierName("kwargs"),
									IdentifierName("Add")
								),
								ArgumentList(SeparatedList(new[] {
									Argument(
										LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(keyword))
									),
									Argument(
										IdentifierName("encodedExpr")
									),
								}))
							))
						)
					);

					stmts.Add(Block(
						ifStatement
					));
					
					break;
				}

				case SourceModelField.FieldMode.Dict {
					DictCodecInstance: var codec,
				}: {
					
					if(hasDict) {
						Context.ReportDiagnostic(Diagnostic.Create(
							Errors.MultipleDict,
							field.Location,
							new object?[] { }
						));
					}

					hasDict = true;


					var encodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(codec),
							IdentifierName("EncodeDict")
						),
						ArgumentList(SeparatedList([
							Argument(propertyValue),
						]))
					);

					var loop = ForEachStatement(
						IdentifierName("var"),
						"kvp",
						encodedExpr,
						Block(
							ExpressionStatement(InvocationExpression(
								MemberAccessExpression(
									SyntaxKind.SimpleMemberAccessExpression,
									IdentifierName("kwargs"),
									IdentifierName("Add")
								),
								ArgumentList(SeparatedList([
									Argument(MemberAccessExpression(SyntaxKind.SimpleMemberAccessExpression, IdentifierName("kvp"), IdentifierName("Key"))),
									Argument(MemberAccessExpression(SyntaxKind.SimpleMemberAccessExpression, IdentifierName("kvp"), IdentifierName("Value"))),
								]))
							))
						)
					);

					stmts.Add(loop);
					
					break;
				}

				case SourceModelField.FieldMode.Vararg {
					VarargCodecInstance: var codec,
					ElementType: var elementType,
					ElementTags: var tags,
				} varargField: {
					PosTagCheck(tags);
					prevOptionalPositionalTags = prevOptionalPositionalTags.Union(tags);

					var encodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(codec),
							IdentifierName("EncodeVararg")
						),
						ArgumentList(SeparatedList([
							Argument(propertyValue),
						]))
					);

					var expr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							IdentifierName("args"),
							IdentifierName("AddRange")
						),
						ArgumentList(SeparatedList([
							Argument(encodedExpr),
						]))
					);

					stmts.Add(ExpressionStatement(expr));
					
					break;
				}

				default:
					throw new Exception("Unexpected field mode");
			}
		}

		stmts.Add(returnStatement);

		return Block(stmts);
	}

	protected BlockSyntax WriteDecodeFields(string constructorName, IReadOnlyList<SourceModelField> fields, TypeSyntax objectType) {
		var stmts = new List<StatementSyntax>();

		var fieldInits = new List<ExpressionSyntax>();
		
		
		

		int positionalIndex = 0;

		foreach(var field in fields) {
			var localName = "local_" + field.Name;

			
			
			switch(field.Mode) {
				case SourceModelField.FieldMode.Normal {
					CodecInstance: var codec,
					DefaultValue: null,
					KeywordMode: SourceModelField.KeywordMode.Positional
				}: {
					DecodePositionalCommon(posBlocks => {
						var exprExpr = posBlocks.exprExpr;
						var pathExpr = posBlocks.pathExpr;
						var sliceStatement = posBlocks.sliceStatement;
						
						var ifCondition = BinaryExpression(
							SyntaxKind.EqualsExpression,
							MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								IdentifierName("args"),
								IdentifierName("Count")),
							LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(0))
						);
						
						var codecExpr = GetCodecExpr(codec);

						var throwStatement = ThrowStatement(
							ObjectCreationExpression(
									QualifiedName(
										QualifiedName(
											AliasQualifiedName(
												IdentifierName(Token(SyntaxKind.GlobalKeyword)),
												IdentifierName("ESExpr")),
											IdentifierName("Runtime")),
										IdentifierName("DecodeException")))
								.WithArgumentList(
									ArgumentList(SeparatedList(new[] {
										Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal("Not enough arguments"))),
										Argument(
											InvocationExpression(
													MemberAccessExpression(
														SyntaxKind.SimpleMemberAccessExpression,
														IdentifierName("path"),
														IdentifierName("WithConstructor"))
												)
												.WithArgumentList(
													ArgumentList(SingletonSeparatedList(
														Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(constructorName))))))
										),
									}))
								)
						);

						var ifStatement = IfStatement(ifCondition, Block(throwStatement));
						stmts.Add(ifStatement);

						var decodedExpr = InvocationExpression(
							MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								codecExpr,
								IdentifierName("Decode")
							),
							ArgumentList(SeparatedList([
								Argument(exprExpr),
								Argument(pathExpr),
							]))
						);

						stmts.Add(LocalDeclarationStatement(
							VariableDeclaration(ConvertTypeToTypeSyntax(field.Type))
								.WithVariables(
									SingletonSeparatedList(
										VariableDeclarator(Identifier(localName))
											.WithInitializer(
												EqualsValueClause(decodedExpr)
											)
									)
								)
						));
						stmts.Add(sliceStatement);
					});
					break;
				}
				
				case SourceModelField.FieldMode.Normal {
					CodecInstance: var codec,
					DefaultValue: {} defaultValue,
					KeywordMode: SourceModelField.KeywordMode.Positional
				}: {
					DecodePositionalCommon(posBlocks => {
						var exprExpr = posBlocks.exprExpr;
						var pathExpr = posBlocks.pathExpr;
						var sliceStatement = posBlocks.sliceStatement;
						
						var codecExpr = GetCodecExpr(codec);

						stmts.Add(LocalDeclarationStatement(
							VariableDeclaration(ConvertTypeToTypeSyntax(field.Type))
								.WithVariables(
									SingletonSeparatedList(
										VariableDeclarator(Identifier(localName))
									)
								)
						));
						
						var ifCondition = BinaryExpression(
							SyntaxKind.LogicalAndExpression,
							BinaryExpression(
								SyntaxKind.NotEqualsExpression,
								MemberAccessExpression(
									SyntaxKind.SimpleMemberAccessExpression,
									IdentifierName("args"),
									IdentifierName("Count")),
								LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(0))
							),
							InvocationExpression(
								MemberAccessExpression(
									SyntaxKind.SimpleMemberAccessExpression,
									MemberAccessExpression(
										SyntaxKind.SimpleMemberAccessExpression,
										codecExpr,
										IdentifierName("Tags")
									),
									IdentifierName("Contains")
								),
								ArgumentList(SingletonSeparatedList(
									Argument(
										MemberAccessExpression(
											SyntaxKind.SimpleMemberAccessExpression,
											ElementAccessExpression(
												IdentifierName("args"),
												BracketedArgumentList(SingletonSeparatedList(
													Argument(LiteralExpression(SyntaxKind.NumericLiteralExpression,
														Literal(0)))
												))
											),
											IdentifierName("Tag")
										)
									)
								))
							)
						);
						
						
						var ifStatement = IfStatement(
							ifCondition,
							Block(
								ExpressionStatement(
									AssignmentExpression(
										SyntaxKind.SimpleAssignmentExpression,
										IdentifierName(localName),
										InvocationExpression(
											MemberAccessExpression(
												SyntaxKind.SimpleMemberAccessExpression,
												codecExpr,
												IdentifierName("Decode")
											),
											ArgumentList(SeparatedList([
												Argument(exprExpr),
												Argument(pathExpr),
											]))
										)
									)
								),
								sliceStatement
							),
							ElseClause(Block(
								ExpressionStatement(
									AssignmentExpression(
										SyntaxKind.SimpleAssignmentExpression,
										IdentifierName(localName),
										defaultValue.Syntax
									)
								)
							))
						);
						stmts.Add(ifStatement);
						
					});
					
					break;
				}
				
				
				case SourceModelField.FieldMode.Normal {
					CodecInstance: var codec,
					KeywordMode: SourceModelField.KeywordMode.Keyword { KeywordName: var keyword }
				} normalField: {
					DecodeKeywordCommon(keyword, pathExpr => {
						var decodedExpr = InvocationExpression(
							MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								GetCodecExpr(codec),
								IdentifierName("Decode")
							),
							ArgumentList(SeparatedList(new[] {
								Argument(IdentifierName("kwargExpr")),
								Argument(pathExpr),
							}))
						);

						BlockSyntax falseBody;

						if(normalField.DefaultValue is { } defaultValue) {
							falseBody = Block(
								ExpressionStatement(AssignmentExpression(
									SyntaxKind.SimpleAssignmentExpression,
									IdentifierName(localName),
									defaultValue.Syntax
								))
							);
						}
						else {
							var throwStatement = ThrowStatement(
								ObjectCreationExpression(
										QualifiedName(
											QualifiedName(
												AliasQualifiedName(
													IdentifierName(Token(SyntaxKind.GlobalKeyword)),
													IdentifierName("ESExpr")),
												IdentifierName("Runtime")),
											IdentifierName("DecodeException")))
									.WithArgumentList(
										ArgumentList(SeparatedList(new[] {
											Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal("Missing required keyword argument " + keyword))),
											Argument(
												InvocationExpression(
														MemberAccessExpression(
															SyntaxKind.SimpleMemberAccessExpression,
															IdentifierName("path"),
															IdentifierName("WithConstructor"))
													)
													.WithArgumentList(
														ArgumentList(SingletonSeparatedList(
															Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(constructorName))))))
											),
										}))
									)
							);

							falseBody = Block(throwStatement);
						}
						
						return (decodedExpr, falseBody);
					});

					
					break;
				}

				case SourceModelField.FieldMode.Optional {
					OptionalValueCodecInstance: var codec,
					KeywordMode: SourceModelField.KeywordMode.Positional { Tags: var tags },
				}: {
					DecodePositionalCommon(posBlocks => {
						var exprExpr = posBlocks.exprExpr;
						var pathExpr = posBlocks.pathExpr;
						var sliceStatement = posBlocks.sliceStatement;
						
						ExpressionSyntax DecodeOptionalExpr(ExpressionSyntax expr) =>
							AssignmentExpression(
								SyntaxKind.SimpleAssignmentExpression,
								IdentifierName(localName),
								InvocationExpression(
									MemberAccessExpression(
										SyntaxKind.SimpleMemberAccessExpression,
										GetCodecExpr(codec),
										IdentifierName("DecodeOptional")
									),
									ArgumentList(SeparatedList([
										Argument(expr),
										Argument(pathExpr),
									]))
								)
							);

						stmts.Add(LocalDeclarationStatement(
							VariableDeclaration(ConvertTypeToTypeSyntax(field.Type))
								.WithVariables(
									SingletonSeparatedList(
										VariableDeclarator(Identifier(localName))
									)
								)
						));

						var ifCondition = BinaryExpression(
							SyntaxKind.LogicalAndExpression,
							BinaryExpression(
								SyntaxKind.NotEqualsExpression,
								MemberAccessExpression(
									SyntaxKind.SimpleMemberAccessExpression,
									IdentifierName("args"),
									IdentifierName("Count")),
								LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(0))
							),
							InvocationExpression(
								MemberAccessExpression(
									SyntaxKind.SimpleMemberAccessExpression,
									MemberAccessExpression(
										SyntaxKind.SimpleMemberAccessExpression,
										GetCodecExpr(codec),
										IdentifierName("ElementTags")
									),
									IdentifierName("Contains")
								),
								ArgumentList(SingletonSeparatedList(
									Argument(
										MemberAccessExpression(
											SyntaxKind.SimpleMemberAccessExpression,
											exprExpr,
											IdentifierName("Tag")
										)
									)
								))
							)
						);

						var ifStatement = IfStatement(
							ifCondition,
							Block(
								ExpressionStatement(DecodeOptionalExpr(exprExpr)),
								sliceStatement
							),
							ElseClause(Block(
								ExpressionStatement(DecodeOptionalExpr(LiteralExpression(SyntaxKind.NullLiteralExpression)))
							))
						);
						stmts.Add(ifStatement);
					});
					break;
				}
				
				case SourceModelField.FieldMode.Optional {
					OptionalValueCodecInstance: var codec,
					KeywordMode: SourceModelField.KeywordMode.Keyword { KeywordName: var keyword }
				}: {
					DecodeKeywordCommon(keyword, pathExpr => {
						var decodedExpr = InvocationExpression(
							MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								GetCodecExpr(codec),
								IdentifierName("DecodeOptional")
							),
							ArgumentList(SeparatedList(new[] {
								Argument(IdentifierName("kwargExpr")),
								Argument(pathExpr),
							}))
						);

						var emptyExpr = InvocationExpression(
							MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								GetCodecExpr(codec),
								IdentifierName("DecodeOptional")
							),
							ArgumentList(SeparatedList(new[] {
								Argument(LiteralExpression(SyntaxKind.NullLiteralExpression)),
								Argument(pathExpr),
							}))
						);

						var falseBody = Block(
							ExpressionStatement(AssignmentExpression(
								SyntaxKind.SimpleAssignmentExpression,
								IdentifierName(localName),
								emptyExpr
							))
						);

						return (decodedExpr, falseBody);
					});
					
					break;
				}

				case SourceModelField.FieldMode.Dict {
					DictCodecInstance: var codec,
				}: {
					var pathExpr = SimpleLambdaExpression(
						Parameter(Identifier("kw")),
						InvocationExpression(
							MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								IdentifierName("path"),
								IdentifierName("Append")
							),
							ArgumentList(SeparatedList([
								Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(constructorName))),
								Argument(
									BinaryExpression(
										SyntaxKind.AddExpression,
										IdentifierName("kw"),
										LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(positionalIndex))
									)
								),
							]))
						)
					);

					var decodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(codec),
							IdentifierName("DecodeDict")
						),
						ArgumentList(SeparatedList([
							Argument(IdentifierName("kwargs")),
							Argument(pathExpr),
						]))
					);

					stmts.Add(LocalDeclarationStatement(
						VariableDeclaration(ConvertTypeToTypeSyntax(field.Type))
							.WithVariables(
								SingletonSeparatedList(
									VariableDeclarator(Identifier(localName))
										.WithInitializer(
											EqualsValueClause(decodedExpr)
										)
								)
							)
					));


					var clearStatement = ExpressionStatement(
						InvocationExpression(
							MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								IdentifierName("kwargs"),
								IdentifierName("Clear")))
					);
					stmts.Add(clearStatement);
					break;
				}

				case SourceModelField.FieldMode.Vararg {
					VarargCodecInstance: var codec,
				}: {
					var pathExpr = SimpleLambdaExpression(
						Parameter(Identifier("i")),
						InvocationExpression(
							MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								IdentifierName("path"),
								IdentifierName("Append")
							),
							ArgumentList(SeparatedList([
								Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(constructorName))),
								Argument(
									BinaryExpression(
										SyntaxKind.AddExpression,
										IdentifierName("i"),
										LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(positionalIndex))
									)
								),
							]))
						)
					);

					var decodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(codec),
							IdentifierName("DecodeVararg")
						),
						ArgumentList(SeparatedList([
							Argument(IdentifierName("args"))
								.WithRefOrOutKeyword(Token(SyntaxKind.RefKeyword)),
							Argument(pathExpr),
						]))
					);

					stmts.Add(LocalDeclarationStatement(
						VariableDeclaration(ConvertTypeToTypeSyntax(field.Type))
							.WithVariables(
								SingletonSeparatedList(
									VariableDeclarator(Identifier(localName))
										.WithInitializer(
											EqualsValueClause(decodedExpr)
										)
								)
							)
					));
					
					break;
				}

				default:
					throw new Exception("Unexpected field mode");
			}

			void DecodeKeywordCommon(string keyword, Func<ExpressionSyntax, (ExpressionSyntax decodedExpr, BlockSyntax falseBody)> buildDecode) {
				stmts.Add(LocalDeclarationStatement(
					VariableDeclaration(ConvertTypeToTypeSyntax(field.Type))
						.WithVariables(
							SingletonSeparatedList(
								VariableDeclarator(Identifier(localName))
							)
						)
				));

				var condition = InvocationExpression(
					MemberAccessExpression(
						SyntaxKind.SimpleMemberAccessExpression,
						IdentifierName("kwargs"),
						IdentifierName("Remove")
					),
					ArgumentList(SeparatedList(new[] {
						Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(keyword))),
						Argument(
							DeclarationExpression(
								IdentifierName("var"),
								SingleVariableDesignation(Identifier("kwargExpr"))
							)
						).WithRefOrOutKeyword(Token(SyntaxKind.OutKeyword)),
					}))
				);

				var pathExpr = InvocationExpression(
					MemberAccessExpression(
						SyntaxKind.SimpleMemberAccessExpression,
						IdentifierName("path"),
						IdentifierName("Append")
					),
					ArgumentList(SeparatedList([
						Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(constructorName))),
						Argument(LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(positionalIndex))),
					]))
				);

				var (decodedExpr, falseBody) = buildDecode(pathExpr);

				var trueBody = Block(
					ExpressionStatement(AssignmentExpression(
						SyntaxKind.SimpleAssignmentExpression,
						IdentifierName(localName),
						decodedExpr
					))
				);

				stmts.Add(Block(IfStatement(
					condition,
					trueBody,
					ElseClause(falseBody)
				)));
			}

			void DecodePositionalCommon(Action<(ExpressionSyntax exprExpr, ExpressionSyntax pathExpr, StatementSyntax sliceStatement)> buildDecode) {
				var exprExpr =
					ElementAccessExpression(IdentifierName("args"))
						.WithArgumentList(
							BracketedArgumentList(SingletonSeparatedList(
								Argument(LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(0)))
							))
						);

				var pathExpr = InvocationExpression(
					MemberAccessExpression(
						SyntaxKind.SimpleMemberAccessExpression,
						IdentifierName("path"),
						IdentifierName("Append")
					),
					ArgumentList(SeparatedList([
						Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(constructorName))),
						Argument(LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(positionalIndex))),
					]))
				);

				// args = args.Slice(1);
				var sliceStatement = ExpressionStatement(
					AssignmentExpression(
						SyntaxKind.SimpleAssignmentExpression,
						IdentifierName("args"),
						InvocationExpression(
								MemberAccessExpression(
									SyntaxKind.SimpleMemberAccessExpression,
									IdentifierName("args"),
									IdentifierName("Slice")))
							.WithArgumentList(
								ArgumentList(SingletonSeparatedList(
									Argument(LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(1)))
								))
							)
					)
				);

				buildDecode((exprExpr, pathExpr, sliceStatement));
			}

			fieldInits.Add(AssignmentExpression(
				SyntaxKind.SimpleAssignmentExpression,
				IdentifierName(field.Name),
				IdentifierName(localName)
			));
		}

		var objExpr = ObjectCreationExpression(objectType)
			.WithInitializer(InitializerExpression(
				SyntaxKind.ObjectInitializerExpression,
				SeparatedList(fieldInits)
			));

		
		
		// if(args.Count > 0) throw new global::ESExpr.Runtime.DecodeException("Extra positional arguments", path.WithConstructor("<name>"));
		stmts.Add(IfStatement(
			BinaryExpression(
				SyntaxKind.GreaterThanExpression,
				MemberAccessExpression(
					SyntaxKind.SimpleMemberAccessExpression,
					IdentifierName("args"),
					IdentifierName("Count")
				),
				LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(0))
			),
			Block(ThrowStatement(
				ObjectCreationExpression(
					QualifiedName(
						QualifiedName(
							AliasQualifiedName(
								IdentifierName(Token(SyntaxKind.GlobalKeyword)),
								IdentifierName("ESExpr")
							),
							IdentifierName("Runtime")
						),
						IdentifierName("DecodeException")
					)
				)
				.WithArgumentList(ArgumentList(SeparatedList([
					Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal("Extra positional arguments"))),
					Argument(InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							IdentifierName("path"),
							IdentifierName("WithConstructor")
						),
						ArgumentList(SingletonSeparatedList(
							Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(constructorName)))
						))
					)),
				])))
			))
		));

		// if(kwargs.Count > 0) throw new global::ESExpr.Runtime.DecodeException("Extra keyword arguments", path.WithConstructor("<name>"));
		stmts.Add(IfStatement(
			BinaryExpression(
				SyntaxKind.GreaterThanExpression,
				MemberAccessExpression(
					SyntaxKind.SimpleMemberAccessExpression,
					IdentifierName("kwargs"),
					IdentifierName("Count")
				),
				LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(0))
			),
			Block(ThrowStatement(
				ObjectCreationExpression(
					QualifiedName(
						QualifiedName(
							AliasQualifiedName(
								IdentifierName(Token(SyntaxKind.GlobalKeyword)),
								IdentifierName("ESExpr")
							),
							IdentifierName("Runtime")
						),
						IdentifierName("DecodeException")
					)
				)
				.WithArgumentList(ArgumentList(SeparatedList([
					Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal("Extra keyword arguments"))),
					Argument(InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							IdentifierName("path"),
							IdentifierName("WithConstructor")
						),
						ArgumentList(SingletonSeparatedList(
							Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(constructorName)))
						))
					)),
				])))
			))
		));
		
		stmts.Add(ReturnStatement(objExpr));

		return Block(stmts);
	}



	protected TypeSyntax StringType => PredefinedType(Token(SyntaxKind.StringKeyword));

	protected NameSyntax ESExprType => QualifiedName(
		QualifiedName(
			AliasQualifiedName(
				IdentifierName(Token(SyntaxKind.GlobalKeyword)),
				IdentifierName("ESExpr")
			),
			IdentifierName("Runtime")
		),
		IdentifierName("Expr")
	);

	protected NameSyntax ESExprTagType => QualifiedName(
		QualifiedName(
			AliasQualifiedName(
				IdentifierName(Token(SyntaxKind.GlobalKeyword)),
				IdentifierName("ESExpr")
			),
			IdentifierName("Runtime")
		),
		IdentifierName("ESExprTag")
	);

	protected NameSyntax ESExprTagSetType => QualifiedName(
		QualifiedName(
			AliasQualifiedName(
				IdentifierName(Token(SyntaxKind.GlobalKeyword)),
				IdentifierName("ESExpr")
			),
			IdentifierName("Runtime")
		),
		IdentifierName("ESExprTagSet")
	);

	protected TypeSyntax ESExprCodecType(TypeSyntax elementType) => QualifiedName(
		QualifiedName(
			AliasQualifiedName(
				IdentifierName(Token(SyntaxKind.GlobalKeyword)),
				IdentifierName("ESExpr")
			),
			IdentifierName("Runtime")
		),
	GenericName(
			Identifier("IESExprCodec"),
			TypeArgumentList(
					SeparatedList([elementType])
			)
		)
	);


	protected TypeSyntax DecodeFailurePathType => QualifiedName(
		QualifiedName(
			AliasQualifiedName(
				IdentifierName(Token(SyntaxKind.GlobalKeyword)),
				IdentifierName("ESExpr")
			),
			IdentifierName("Runtime")
		),
		IdentifierName("DecodeFailurePath")
	);

	protected TypeSyntax DictionaryType(TypeSyntax keyType, TypeSyntax valueType) =>
		QualifiedName(
			QualifiedName(
				QualifiedName(
					AliasQualifiedName(
						IdentifierName(Token(SyntaxKind.GlobalKeyword)),
						IdentifierName("System")
					),
					IdentifierName("Collections")
				),
				IdentifierName("Generic")
			),
			GenericName(
				Identifier("Dictionary"),
				TypeArgumentList(
					SeparatedList([keyType, valueType])
				)
			)
		);

	protected TypeSyntax OptionType(TypeSyntax elementType) =>
		QualifiedName(
			QualifiedName(
				AliasQualifiedName(
					IdentifierName(Token(SyntaxKind.GlobalKeyword)),
					IdentifierName("ESExpr")
				),
				IdentifierName("Runtime")
			),
			GenericName(
				Identifier("Option"),
				TypeArgumentList(
					SeparatedList([elementType])
				)
			)
		);

	protected static TypeSyntax GetDeclarationAsType(string name, IReadOnlyList<SourceModelSyntax<TypeParameterSyntax>> typeParameters) {
		if(typeParameters.Count == 0) {
			return IdentifierName(name);
		}

		return GenericName(
			Identifier(name),
			TypeArgumentList(
				SeparatedList(
					typeParameters.Select(tp => {
						TypeSyntax tpType = IdentifierName(tp.Syntax.Identifier.ToString());
						return tpType;
					})
				)
			)
		);
	}

	private NameSyntax? GetNamespaceName() {
		NameSyntax? ns = null;

		foreach(var part in TypeModel.Namespace) {
			if(ns is null) {
				ns = IdentifierName(part);
			}
			else {
				ns = QualifiedName(ns, IdentifierName(part));
			}
		}

		return ns;
	}

	protected static NameSyntax? NameFromNamespaceNodes(SyntaxNode? syntax) {
		if(syntax is not BaseNamespaceDeclarationSyntax ns) {
			return null;
		}

		var parentNS = NameFromNamespaceNodes(ns.Parent);

		if(parentNS is null) {
			return ns.Name;
		}

		return MergeNames(parentNS, ns.Name);
	}

	protected static NameSyntax MergeNames(NameSyntax a, NameSyntax b) {
		return b switch {
			QualifiedNameSyntax qb => QualifiedName(
				MergeNames(a, qb.Left),
				qb.Right
			),
			SimpleNameSyntax sb => QualifiedName(a, sb),
			_ => throw new ArgumentException(
				"The right NameSyntax must be a SimpleNameSyntax or QualifiedNameSyntax.",
				nameof(b)
			),
		};
	}


	protected ExpressionSyntax GetCodecExpr(SourceModelTypeClassInstance codec) {
		switch(codec) {
			case SourceModelTypeClassInstance.Local { Name: var name }:
				return IdentifierName("instance_" + name);
			
			case SourceModelTypeClassInstance.Property property:
				return MemberAccessExpression(
					SyntaxKind.SimpleMemberAccessExpression,
					ConvertTypeToTypeSyntax(property.DeclaringType),
					IdentifierName(property.Name)
				);
			
			case SourceModelTypeClassInstance.Method method:
				return InvocationExpression(
					MemberAccessExpression(
						SyntaxKind.SimpleMemberAccessExpression,
						ConvertTypeToTypeSyntax(method.DeclaringType),

						method.TypeArguments.IsEmpty
							? IdentifierName(method.Name)
							: GenericName(method.Name).WithTypeArgumentList(TypeArgumentList(
									SeparatedList<TypeSyntax>(
										method.TypeArguments.Select(ConvertTypeToTypeSyntax)
									)
								)
							)
					),
					ArgumentList(SeparatedList<ArgumentSyntax>(
						method.Arguments.Select(arg => Argument(GetCodecExpr(arg)))
					))
				);
			
			default:
				throw new ArgumentException("Unknown SourceModelTypeClassInstance codec", nameof(codec));
		}
	}
	
	protected static TypeSyntax ConvertTypeToTypeSyntax(SourceModelType t) {
		switch(t) {
			case SourceModelType.NamedSymbol namedTypeSymbol: {
				return GetNamedSymbolSyntax(namedTypeSymbol);
			}

			case SourceModelType.Array arrayTypeSymbol:
				var elementTypeSyntax = ConvertTypeToTypeSyntax(arrayTypeSymbol.Element);
				return ArrayType(elementTypeSyntax, SingletonList(ArrayRankSpecifier()));

			case SourceModelType.Pointer pointerTypeSymbol:
				var pointedAtTypeSyntax = ConvertTypeToTypeSyntax(pointerTypeSymbol.PointedAtType);
				return PointerType(pointedAtTypeSyntax);

			case SourceModelType.TypeParameter typeParameterSymbol:
				return IdentifierName(typeParameterSymbol.Name);

			case SourceModelType.Nullable nullableType:
				return NullableType(ConvertTypeToTypeSyntax(nullableType.Inner));

			default:
				throw new Exception("Unexpected SourceModelType");
		}
	}
	
	private static NameSyntax GetNamedSymbolSyntax(SourceModelType.NamedSymbol namedTypeSymbol) {
		SimpleNameSyntax name;
		if(namedTypeSymbol.TypeArguments.Count != 0) {
			var genericArguments = namedTypeSymbol.TypeArguments.Select(ConvertTypeToTypeSyntax);
			name = GenericName(
				Identifier(namedTypeSymbol.Name),
				TypeArgumentList(SeparatedList(genericArguments))
			);
		}
		else {
			name = IdentifierName(namedTypeSymbol.Name);
		}

		return GetNamedParentMemberSyntax(namedTypeSymbol.Parent, name);
	}
	
	private static NameSyntax GetNamedParentMemberSyntax(SourceModelType.INamedSymbolParent parent, SimpleNameSyntax name) {
		switch(parent) {
			case SourceModelType.NamespaceSymbolParent nsParent:
				return GetNamespaceMemberSyntax(nsParent.Namespace, nsParent.Namespace.Count, name);
			
			case SourceModelType.NamedSymbol namedParent:
				return QualifiedName(GetNamedSymbolSyntax(namedParent), name);
			
			default:
				throw new Exception("Unexpected parent type");
		}
	}

	private static NameSyntax GetNamespaceMemberSyntax(ImmutableList<string> ns, int nsPartCount, SimpleNameSyntax memberName) {
		if(nsPartCount > 0) {
			return QualifiedName(GetNamespaceMemberSyntax(ns, nsPartCount - 1, IdentifierName(ns[nsPartCount - 1])), memberName);
		}
		else {
			return AliasQualifiedName(
				IdentifierName(Token(SyntaxKind.GlobalKeyword)),
				memberName
			);
		}
	}

	private string PascalCaseToCamelCase(string name) =>
		name.Length == 0
			? name
			: name.Substring(0, 1).ToLowerInvariant() + name.Substring(1);


}

internal class AbortGenerationException : Exception {
	public AbortGenerationException(Diagnostic diag) {
		Diagnostic = diag;
	}

	public Diagnostic Diagnostic { get; }
}
