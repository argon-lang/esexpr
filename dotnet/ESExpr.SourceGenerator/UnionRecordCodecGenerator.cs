using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using Microsoft.CodeAnalysis;
using static Microsoft.CodeAnalysis.CSharp.SyntaxFactory;

namespace ESExpr.SourceGenerator;

internal class UnionRecordCodecGenerator : CodecGenerator<RecordDeclarationSyntax> {
	
	private IEnumerable<RecordDeclarationSyntax> Cases => Decl.Members
		.OfType<RecordDeclarationSyntax>()
		.Where(caseDecl => caseDecl.Modifiers.Any(SyntaxKind.PublicKeyword));

	private (PropertyDeclarationSyntax, ITypeSymbol) GetInlineValueProp(RecordDeclarationSyntax c) {
		var props = c.Members.OfType<PropertyDeclarationSyntax>().ToList();

		if(props.Count != 1) {
			throw new AbortGenerationException(
				Diagnostic.Create(
					Errors.InvalidInlineValue,
					c.Identifier.GetLocation(),
					new object[] { }
				)
			);
		}

		var prop = props[0];
		var propType = SemanticModel.GetTypeInfo(prop.Type).Type;
		if(propType == null) {
			throw new Exception("Could not resolve type.");
		}
		
		return (prop, propType);
	}
	
	protected override ExpressionSyntax GenerateTagsBody() {
		return CastExpression(
			QualifiedName(
				QualifiedName(
					QualifiedName(
						AliasQualifiedName(
							IdentifierName(Token(SyntaxKind.GlobalKeyword)),
							IdentifierName("System")),
						IdentifierName("Collections")
					),
					IdentifierName("Generic")
				),
				GenericName(Identifier("HashSet"))
					.WithTypeArgumentList(
						TypeArgumentList(SingletonSeparatedList<TypeSyntax>(
							ESExprTagType
						))
					)
			),
			CollectionExpression(
				SeparatedList<CollectionElementSyntax>(
					Cases.Select<RecordDeclarationSyntax, CollectionElementSyntax>(c => {
						if(IsInlineValue(c)) {
							var (_, propType) = GetInlineValueProp(c);

							return SpreadElement(
								MemberAccessExpression(
									SyntaxKind.SimpleMemberAccessExpression,
									GetCodecExpr(propType),
									IdentifierName("Tags")
								)
							);
						}
						else {
							return ExpressionElement(
								ObjectCreationExpression(
										QualifiedName(
											ESExprTagType,
											IdentifierName("Constructor")
										)
									)
									.WithArgumentList(
										ArgumentList(SingletonSeparatedList(
											Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(GetConstructorName(c))))
										))
									)
							);
						}
					})
				)
			)
		);
	}

	protected override BlockSyntax GenerateEncodeBody() {
		var cases = new List<SwitchSectionSyntax>();

		foreach(var c in Cases) {
			var identName = "value2";

			var label = CasePatternSwitchLabel(
				DeclarationPattern(
					IdentifierName(c.Identifier.Text),
					SingleVariableDesignation(Identifier(identName))
				),
				Token(SyntaxKind.ColonToken)
			);

			StatementSyntax switchBody;

			if(IsInlineValue(c)) {
				var (prop, propType) = GetInlineValueProp(c);
				
				
				switchBody = ReturnStatement(
					InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(propType),
							IdentifierName("Encode")
						),
						ArgumentList(SeparatedList(new ArgumentSyntax[] {
							Argument(MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								IdentifierName(identName),
								IdentifierName(prop.Identifier.Text)
							)),
						}))
					)
				);
			}
			else {
				switchBody = WriteEncodeFields(c, IdentifierName(identName));
			}

			cases.Add(SwitchSection(
				List(new SwitchLabelSyntax[] { label }),
				List(new StatementSyntax[] {
					switchBody,
				})
			));
		}
		
		cases.Add(SwitchSection(
			List(new SwitchLabelSyntax[] { DefaultSwitchLabel() }),
			List(new StatementSyntax[] {
				ParseStatement("throw new global::System.InvalidOperationException(\"Unexpected instance type\");"),
			})
		));
		
		var swStmt = SwitchStatement(IdentifierName("value"), List(cases));

		return Block(swStmt);
	}

	protected override BlockSyntax GenerateDecodeBody() {
		var cases = new List<SwitchSectionSyntax>();

		foreach(var c in Cases) {
			var constructorName = GetConstructorName(c);

			if(IsInlineValue(c)) {
				var (prop, propType) = GetInlineValueProp(c);
				
				var label = CasePatternSwitchLabel(
					VarPattern(DiscardDesignation()),
					Token(SyntaxKind.ColonToken)
				).WithWhenClause(
					WhenClause(InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							
							MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								GetCodecExpr(propType),
								IdentifierName("Tags")
							),
							IdentifierName("Contains")
						),
						ArgumentList(SeparatedList(new ArgumentSyntax[] {
							Argument(MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								IdentifierName("expr"),
								IdentifierName("Tag")
							)),
						}))
					))
				);
				
				cases.Add(SwitchSection(
					List(new SwitchLabelSyntax[] { label }),
					List(new StatementSyntax[] {
						ReturnStatement(
							ObjectCreationExpression(IdentifierName(c.Identifier.Text))
								.WithInitializer(InitializerExpression(
									SyntaxKind.ObjectInitializerExpression,
									SeparatedList(new ExpressionSyntax[] {
										AssignmentExpression(
											SyntaxKind.SimpleAssignmentExpression,
											IdentifierName(prop.Identifier.Text),
											InvocationExpression(
												MemberAccessExpression(
													SyntaxKind.SimpleMemberAccessExpression,
													GetCodecExpr(propType),
													IdentifierName("Decode")
												),
												ArgumentList(SeparatedList(new ArgumentSyntax[] {
													Argument(IdentifierName("expr")),
													Argument(IdentifierName("path")),
												}))
											)
										)
									})
								))
						),
					})
				));	
				
			}
			else {
				var pattern =
					RecursivePattern()
						.WithType(
							QualifiedName(
								QualifiedName(
									QualifiedName(
										AliasQualifiedName(
											IdentifierName(Token(SyntaxKind.GlobalKeyword)),
											IdentifierName("ESExpr")
										),
										IdentifierName("Runtime")),
									IdentifierName("Expr")
								),
								IdentifierName("Constructor")
							)
						)
						.WithPositionalPatternClause(
							PositionalPatternClause(SeparatedList<SubpatternSyntax>([
								Subpattern(ConstantPattern(
									LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(constructorName))
								)),
								Token(SyntaxKind.CommaToken),
								Subpattern(DeclarationPattern(
									IdentifierName("var"),
									SingleVariableDesignation(Identifier("args0"))
								)),
								Token(SyntaxKind.CommaToken),
								Subpattern(DeclarationPattern(
									IdentifierName("var"),
									SingleVariableDesignation(Identifier("kwargs0"))
								)),
							]))
						);

				var label = CasePatternSwitchLabel(
					pattern,
					Token(SyntaxKind.ColonToken)
				);
			

				var argsDeclaration = ParseStatement("var args = new global::ESExpr.Runtime.SliceList<global::ESExpr.Runtime.Expr>(args0);");
				var kwargsDeclaration = ParseStatement("var kwargs = new global::System.Collections.Generic.Dictionary<string, global::ESExpr.Runtime.Expr>(kwargs0);");
			
			
				var decodeBlock = WriteDecodeFields(c);

				cases.Add(SwitchSection(
					List(new SwitchLabelSyntax[] { label }),
					List(new StatementSyntax[] {
						Block((IEnumerable<StatementSyntax>)[
							argsDeclaration,
							kwargsDeclaration,
							..decodeBlock.Statements,
						]),
					})
				));	
			}
		}
		
		cases.Add(SwitchSection(
			List(new SwitchLabelSyntax[] { DefaultSwitchLabel() }),
			List(new StatementSyntax[] {
				ParseStatement("throw new global::ESExpr.Runtime.DecodeException(\"Unexpected value for enum\", path);"),
			})
		));
		
		var swStmt = SwitchStatement(IdentifierName("expr"), List(cases));

		return Block(swStmt);
	}
}
