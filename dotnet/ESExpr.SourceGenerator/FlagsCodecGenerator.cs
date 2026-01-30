using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Linq.Expressions;
using System.Numerics;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using static Microsoft.CodeAnalysis.CSharp.SyntaxFactory;

namespace ESExpr.SourceGenerator;

internal class FlagsCodecGenerator : CodecGenerator<FlagsSourceModel> {
	protected override BlockSyntax GenerateIsEqualBody() {
		return Block(
		ReturnStatement(
			InvocationExpression(
				MemberAccessExpression(
					SyntaxKind.SimpleMemberAccessExpression,
					IdentifierName("a"),
					IdentifierName("Equals")
				),
				ArgumentList(SingletonSeparatedList(
					Argument(IdentifierName("b"))
				))
			)
		)
		);
	}

	protected override BlockSyntax GenerateEncodeBody() {
		var stmts = new List<StatementSyntax>();
		
		stmts.Add(LocalDeclarationStatement(
			VariableDeclaration(
				BigIntegerType,
				SingletonSeparatedList(
					VariableDeclarator(Identifier("bits"))
						.WithInitializer(EqualsValueClause(QualifiedName(
							BigIntegerType,
							IdentifierName("Zero")
						)))
				)
			)
		));


		StatementSyntax SetBits(ExpressionSyntax bitsExpr) =>
			ExpressionStatement(AssignmentExpression(
				SyntaxKind.OrAssignmentExpression,
				IdentifierName("bits"),
				bitsExpr
			));

		BigInteger usedBits = BigInteger.Zero;
		foreach(var field in TypeModel.Fields) {
			switch(field) {
				case SourceModelFlagsFieldFlag flagField: {
					if((usedBits & flagField.Mask) != BigInteger.Zero) {
						throw new InvalidOperationException("Duplicate flag mask");
					}
					
					if(flagField.Mask < 0) {
						throw new InvalidOperationException("Negative mask not supported");
					}

					CheckFlagBits(flagField.Mask);

					var maskExpr = BigIntLiteral(flagField.Mask);
					
					
					stmts.Add(IfStatement(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							IdentifierName("value"),
							IdentifierName(field.Name)
						),
						SetBits(maskExpr)
					));
					
					break;
				}



				case SourceModelFlagsFieldEnum enumField: {
					var mask = GetEnumMask(enumField);

					if((usedBits & mask) != BigInteger.Zero) {
						throw new InvalidOperationException("Duplicate enum mask");
					}
					usedBits |= mask;
					
					var bitsValue = SwitchExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							IdentifierName("value"),
							IdentifierName(enumField.Name)
						),
						SeparatedList(
							enumField.Cases.Select(enumCase =>
								SwitchExpressionArm(
									ConstantPattern(
										MemberAccessExpression(
											SyntaxKind.SimpleMemberAccessExpression,
											ConvertTypeToTypeSyntax(enumField.Type),
											IdentifierName(enumCase.Name)
										)
									),
									BigIntLiteral(enumCase.Value)
								)
							).Append(
								SwitchExpressionArm(
									DiscardPattern(),
									ThrowExpression(
										ObjectCreationExpression(
												QualifiedName(
													AliasQualifiedName(
														IdentifierName(Token(SyntaxKind.GlobalKeyword)),
														IdentifierName("System")
													),
													IdentifierName("InvalidOperationException")
												)
											)
											.WithArgumentList(
												ArgumentList(SeparatedList<ArgumentSyntax>([
													Argument(
														LiteralExpression(SyntaxKind.StringLiteralExpression, Literal($"Invalid value for {field.Name}"))
													),
												]))
											)
									)
								)
							)
						)
					);

					stmts.Add(SetBits(bitsValue));

					break;					
				}

				
				default:
					throw new InvalidOperationException($"Unknown flags field type: {field.GetType().Name}");
			}
		}

		
		stmts.Add(ReturnStatement(
			ObjectCreationExpression(
				QualifiedName(
					QualifiedName(
						QualifiedName(
							AliasQualifiedName(
								IdentifierName(Token(SyntaxKind.GlobalKeyword)),
								IdentifierName("ESExpr")
							),
							IdentifierName("Runtime")
						),
						IdentifierName("Expr")
					),
					IdentifierName("Int")
				)
			)
			.WithArgumentList(
				ArgumentList(SingletonSeparatedList(
					Argument(IdentifierName("bits"))
				))
			)
		));

		return Block(stmts);
	}

	protected override BlockSyntax GenerateDecodeBody() {
		var stmts = new List<StatementSyntax>();

		// if(!(expr is global::ESExpr.Runtime.Expr.Int(var value)) || value < 0)
		var ifCondition = BinaryExpression(
			SyntaxKind.LogicalOrExpression,
			PrefixUnaryExpression(
				SyntaxKind.LogicalNotExpression,
				ParenthesizedExpression(
					IsPatternExpression(
						IdentifierName("expr"),
						RecursivePattern()
							.WithType(
								QualifiedName(
									QualifiedName(
										QualifiedName(
											AliasQualifiedName(
												IdentifierName(Token(SyntaxKind.GlobalKeyword)),
												IdentifierName("ESExpr")
											),
											IdentifierName("Runtime")
										),
										IdentifierName("Expr")
									),
									IdentifierName("Int")
								)
							)
							.WithPositionalPatternClause(
								PositionalPatternClause(SingletonSeparatedList(
									Subpattern(DeclarationPattern(
										IdentifierName("var"),
										SingleVariableDesignation(Identifier("bits"))
									))
								))
							)
					)
				)
			),
			BinaryExpression(
				SyntaxKind.LessThanExpression,
				IdentifierName("bits"),
				LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(0))
			)
		);

		var throwStatement = ThrowStatement(
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
			.WithArgumentList(
				ArgumentList(SeparatedList<ArgumentSyntax>([
					Argument(
						LiteralExpression(SyntaxKind.StringLiteralExpression, Literal("Expected a non-negative int for flags"))
					),
					Token(SyntaxKind.CommaToken),
					Argument(IdentifierName("path")),
				]))
			)
		);

		stmts.Add(IfStatement(ifCondition, Block(throwStatement)));

		var recordType = GetDeclarationAsType(TypeModel.TypeName, TypeModel.TypeParameters);

		stmts.Add(ReturnStatement(
			ObjectCreationExpression(recordType)
				.WithInitializer(
					InitializerExpression(
						SyntaxKind.ObjectInitializerExpression,
						SeparatedList<ExpressionSyntax>(
							TypeModel.Fields.Select(field => {
								ExpressionSyntax fieldValue;
								
								switch(field) {
									case SourceModelFlagsFieldFlag flagField: {
										fieldValue = BinaryExpression(
											SyntaxKind.NotEqualsExpression,
											ParenthesizedExpression(
												BinaryExpression(
													SyntaxKind.BitwiseAndExpression,
													IdentifierName("bits"),
													BigIntLiteral(flagField.Mask)
												)
											),
											LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(0))
										);

										break;
									}

									case SourceModelFlagsFieldEnum enumField: {
										var mask = GetEnumMask(enumField);

										var maskExpr = BigIntLiteral(mask);

										// var <fieldName> = <fieldName>Bits switch { ... };
										fieldValue = SwitchExpression(
											ParenthesizedExpression(
												BinaryExpression(
													SyntaxKind.BitwiseAndExpression,
													IdentifierName("bits"),
													maskExpr
												)
											),
											SeparatedList(
												enumField.Cases.Select(enumCase => {
													var caseValue = MemberAccessExpression(
														SyntaxKind.SimpleMemberAccessExpression,
														ConvertTypeToTypeSyntax(enumField.Type),
														IdentifierName(enumCase.Name)
													);
													
													var bigIntValue = BigIntLiteral(enumCase.Value);
													return SwitchExpressionArm(
														VarPattern(SingleVariableDesignation(Identifier("maskedValue"))),
														WhenClause(
															BinaryExpression(
																SyntaxKind.EqualsExpression,
																IdentifierName("maskedValue"),
																bigIntValue
															)
														),
														caseValue
													);
												}).Append(
													SwitchExpressionArm(
														DiscardPattern(),
														ThrowExpression(
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
															.WithArgumentList(
																ArgumentList(SeparatedList<ArgumentSyntax>([
																	Argument(
																		LiteralExpression(SyntaxKind.StringLiteralExpression, Literal($"Invalid value for {field.Name}"))
																	),
																	Token(SyntaxKind.CommaToken),
																	Argument(IdentifierName("path")),
																]))
															)
														)
													)
												)
											)
										);

										break;
									}

									default:
										throw new InvalidOperationException($"Unknown flags field type: {field.GetType().Name}");
								}
								
								return AssignmentExpression(
									SyntaxKind.SimpleAssignmentExpression,
									IdentifierName(field.Name),
									fieldValue
								);
							})
						)
					)
				)
		));

		return Block(stmts);
	}


	private void CheckFlagBits(BigInteger mask) {
		bool hasBit = false;
		uint bitIndex = 0;
		foreach(var maskByte in mask.ToByteArray()) {
			byte bits = maskByte;
			for(int i = 0; i < 8; ++i, ++bitIndex, bits >>= 1) {
				if((bits & 1) == 1) {
					if(hasBit) {
						throw new InvalidOperationException("Flag mask must have only one bit set");
					}
					
					hasBit = true;
				}
			}
		}

		if(!hasBit) {
			throw new InvalidOperationException("Flag mask must have at least one bit set");
		}
	}

	private QualifiedNameSyntax BigIntegerType =>
		QualifiedName(
			QualifiedName(
				AliasQualifiedName(IdentifierName(Token(SyntaxKind.GlobalKeyword)), IdentifierName("System")),
				IdentifierName("Numerics")
			),
			IdentifierName("BigInteger")
		);

	private ExpressionSyntax BigIntLiteral(BigInteger value) =>
		value <= ulong.MaxValue
			? LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal((ulong)value))
			: InvocationExpression(
				MemberAccessExpression(
					SyntaxKind.SimpleMemberAccessExpression,
					BigIntegerType,
					IdentifierName("Parse")
				),
				ArgumentList(SeparatedList([
					Argument(LiteralExpression(
						SyntaxKind.StringLiteralExpression,
						Literal(value.ToString(CultureInfo.InvariantCulture))
					)),
					Argument(QualifiedName(
						QualifiedName(
							QualifiedName(
								AliasQualifiedName(IdentifierName(Token(SyntaxKind.GlobalKeyword)),
									IdentifierName("System")),
								IdentifierName("Globalization")
							),
							IdentifierName("CultureInfo")
						),
						IdentifierName("InvariantCulture")
					))
				]))
			);
	

	private BigInteger GetEnumMask(SourceModelFlagsFieldEnum enumField) {
		var mask = BigInteger.Zero;
		foreach(var enumCase in enumField.Cases) {
			if(enumCase.Value < 0) {
				throw new InvalidOperationException("Negative enum value not supported");
			}
						
			mask |= enumCase.Value;
		}

		return mask;
	}
}
