using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using Microsoft.CodeAnalysis;
using static Microsoft.CodeAnalysis.CSharp.SyntaxFactory;

namespace ESExpr.SourceGenerator;

internal class UnionRecordCodecGenerator : CodecGenerator<UnionRecordSourceModel> {
	private NameSyntax GetCaseType(SourceModelEnumCase c) =>
		QualifiedName((NameSyntax)GetDeclarationAsType(TypeModel.TypeName, TypeModel.TypeParameters), IdentifierName(c.Name));

	private static SourceModelField GetInlineValueField(SourceModelEnumCase c) {
		if(c.Fields.Count != 1) {
			throw new AbortGenerationException(
				Diagnostic.Create(
					Errors.InvalidInlineValue,
					c.Location,
					Array.Empty<object>()
				)
			);
		}

		return c.Fields[0];
	}

	protected override BlockSyntax GenerateIsEqualBody() {
		var arms = new List<SwitchExpressionArmSyntax>();
		var helpers = new List<StatementSyntax>();

		foreach(var c in TypeModel.Cases) {
			var pattern = DeclarationPattern(
				GetCaseType(c),
				SingleVariableDesignation(Identifier("a2"))
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
										GetCaseType(c),
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

			var helperName = "IsEqualCase" + arms.Count;
			helpers.Add(LocalFunctionStatement(PredefinedType(Token(SyntaxKind.BoolKeyword)), Identifier(helperName))
				.WithParameterList(ParameterList(SingletonSeparatedList(
					Parameter(Identifier("a2")).WithType(GetCaseType(c)))))
				.WithBody(switchBody));
			arms.Add(SwitchExpressionArm(pattern,
				InvocationExpression(IdentifierName(helperName),
					ArgumentList(SingletonSeparatedList(Argument(IdentifierName("a2")))))));
		}

		return Block(new StatementSyntax[] {
			ReturnStatement(SwitchExpression(IdentifierName("a"), SeparatedList(arms))),
		}.Concat(helpers));
	}

	protected override BlockSyntax GenerateEncodeBody() {
		var arms = new List<SwitchExpressionArmSyntax>();
		var helpers = new List<StatementSyntax>();


		foreach(var c in TypeModel.Cases) {
			var identName = "value2";

			var pattern = DeclarationPattern(
				GetCaseType(c),
				SingleVariableDesignation(Identifier(identName))
			);

			StatementSyntax switchBody;

			if(c.IsInlineValue) {
				var field = GetInlineValueField(c);


				if(field.Mode is not SourceModelField.FieldMode.Normal {
					CodecInstance: var codec,
				}) {
					throw new InvalidOperationException("Inline declaration was not validated before emission");
				}


				switchBody = ReturnStatement(
					InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(codec),
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

			var helperName = "EncodeCase" + arms.Count;
			helpers.Add(LocalFunctionStatement(ESExprType, Identifier(helperName))
				.WithParameterList(ParameterList(SingletonSeparatedList(
					Parameter(Identifier("value2")).WithType(GetCaseType(c)))))
				.WithBody(switchBody as BlockSyntax ?? Block(switchBody)));
			arms.Add(SwitchExpressionArm(pattern,
				InvocationExpression(IdentifierName(helperName),
					ArgumentList(SingletonSeparatedList(Argument(IdentifierName("value2")))))));
		}

		return Block(new StatementSyntax[] {
			ReturnStatement(SwitchExpression(IdentifierName("value"), SeparatedList(arms))),
		}.Concat(helpers));
	}

	protected override BlockSyntax GenerateDecodeBody() {
		var cases = new List<SwitchSectionSyntax>();

		foreach(var c in TypeModel.Cases) {
			var constructorName = c.ConstructorName;

			if(c.IsInlineValue) {
				var field = GetInlineValueField(c);


				if(field.Mode is not SourceModelField.FieldMode.Normal {
					CodecInstance: var codec,
				}) {
					throw new InvalidOperationException("Inline declaration was not validated before emission");
				}

				var label = CasePatternSwitchLabel(
					VarPattern(DiscardDesignation()),
					Token(SyntaxKind.ColonToken)
				).WithWhenClause(
					WhenClause(InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,

							MemberAccessExpression(
								SyntaxKind.SimpleMemberAccessExpression,
								GetCodecExpr(codec),
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
							ObjectCreationExpression(GetCaseType(c))
								.WithInitializer(InitializerExpression(
									SyntaxKind.ObjectInitializerExpression,
									SeparatedList(new ExpressionSyntax[] {
										AssignmentExpression(
											SyntaxKind.SimpleAssignmentExpression,
											IdentifierName(field.Name),
											InvocationExpression(
												MemberAccessExpression(
													SyntaxKind.SimpleMemberAccessExpression,
													GetCodecExpr(codec),
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

				var caseType = GetCaseType(c);

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
