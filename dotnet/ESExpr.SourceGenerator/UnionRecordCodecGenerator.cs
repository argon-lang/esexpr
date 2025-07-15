using System.Collections.Generic;
using System.Linq;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using Microsoft.CodeAnalysis;
using static Microsoft.CodeAnalysis.CSharp.SyntaxFactory;

namespace ESExpr.SourceGenerator;

internal class UnionRecordCodecGenerator : CodecGenerator<UnionRecordSourceModel> {
	private SourceModelField GetInlineValueField(SourceModelEnumCase c) {
		if(c.Fields.Count != 1) {
			throw new AbortGenerationException(
				Diagnostic.Create(
					Errors.InvalidInlineValue,
					c.Location,
					new object[] { }
				)
			);
		}

		return c.Fields[0];
	}

	protected override BlockSyntax GenerateIsEqualBody() {
		var cases = new List<SwitchSectionSyntax>();

		foreach(var c in TypeModel.Cases) {
			

			var label = CasePatternSwitchLabel(
				DeclarationPattern(
					IdentifierName(c.Name),
					SingleVariableDesignation(Identifier("a2"))
				),
				Token(SyntaxKind.ColonToken)
			);

			BlockSyntax switchBody = WriteIsEqualFields(c.Fields, IdentifierName("a2"), IdentifierName("b2"));
			switchBody = Block(
				switchBody.Statements.Insert(
					0,
					IfStatement(
						PrefixUnaryExpression(
							SyntaxKind.LogicalNotExpression,
							ParenthesizedExpression(
								IsPatternExpression(
									IdentifierName("b"),
									DeclarationPattern(
										IdentifierName(c.Name),
										SingleVariableDesignation(
											Identifier("b2")
										)
									)
								)
							)
						),
						Block(
							ReturnStatement(
								LiteralExpression(
									SyntaxKind.FalseLiteralExpression
								)
							)
						)
					)
				)
			);
			
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

		var swStmt = SwitchStatement(IdentifierName("a"), List(cases));

		return Block(swStmt);
	}

	protected override BlockSyntax GenerateEncodeBody() {
		var cases = new List<SwitchSectionSyntax>();

		foreach(var c in TypeModel.Cases) {
			var identName = "value2";

			var label = CasePatternSwitchLabel(
				DeclarationPattern(
					IdentifierName(c.Name),
					SingleVariableDesignation(Identifier(identName))
				),
				Token(SyntaxKind.ColonToken)
			);

			StatementSyntax switchBody;

			if(c.IsInlineValue) {
				var field = GetInlineValueField(c);


				switchBody = ReturnStatement(
					InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(field.Type),
							IdentifierName("Encode")
						),
						ArgumentList(SeparatedList(new ArgumentSyntax[] {
							Argument(MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								IdentifierName(identName),
								IdentifierName(field.Name)
							)),
						}))
					)
				);
			}
			else {
				switchBody = WriteEncodeFields(c.ConstructorName, c.Fields, IdentifierName(identName));
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

		foreach(var c in TypeModel.Cases) {
			var constructorName = c.ConstructorName;

			if(c.IsInlineValue) {
				var field = GetInlineValueField(c);

				var label = CasePatternSwitchLabel(
					VarPattern(DiscardDesignation()),
					Token(SyntaxKind.ColonToken)
				).WithWhenClause(
					WhenClause(InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,

							MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								GetCodecExpr(field.Type),
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
							ObjectCreationExpression(IdentifierName(c.Name))
								.WithInitializer(InitializerExpression(
									SyntaxKind.ObjectInitializerExpression,
									SeparatedList(new ExpressionSyntax[] {
										AssignmentExpression(
											SyntaxKind.SimpleAssignmentExpression,
											IdentifierName(field.Name),
											InvocationExpression(
												MemberAccessExpression(
													SyntaxKind.SimpleMemberAccessExpression,
													GetCodecExpr(field.Type),
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

				var caseType = GetDeclarationAsType(c.Name, TypeModel.TypeParameters);

				var decodeBlock = WriteDecodeFields(c.ConstructorName, c.Fields, caseType);

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
