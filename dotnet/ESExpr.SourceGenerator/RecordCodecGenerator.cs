using System.Linq;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using static Microsoft.CodeAnalysis.CSharp.SyntaxFactory;

namespace ESExpr.SourceGenerator;

internal class RecordCodecGenerator : CodecGenerator<RecordDeclarationSyntax> {
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
				SingletonSeparatedList<CollectionElementSyntax>(ExpressionElement(
					ObjectCreationExpression(
							QualifiedName(
								ESExprTagType,
								IdentifierName("Constructor")
							)
						)
						.WithArgumentList(
							ArgumentList(SingletonSeparatedList(
								Argument(LiteralExpression(SyntaxKind.StringLiteralExpression, Literal(GetConstructorName(Decl))))
							))
						)
				))				
			)
		);
	}

	protected override BlockSyntax GenerateEncodeBody() =>
		WriteEncodeFields(Decl, IdentifierName("value"));

	protected override BlockSyntax GenerateDecodeBody() {
		var constructorName = GetConstructorName(Decl);
		
        // if(expr is global::ESExpr.Runtime.ESExpr.Constructor("name", var args0, var kwargs0))
        var ifCondition = IsPatternExpression(
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
                )
        );

        var argsDeclaration = ParseStatement("var args = new global::ESExpr.Runtime.SliceList<global::ESExpr.Runtime.Expr>(args0);");
        var kwargsDeclaration = ParseStatement("var kwargs = new global::System.Collections.Generic.Dictionary<string, global::ESExpr.Runtime.Expr>(kwargs0);");
        
        // throw new global::ESExpr.Runtime.DecodeException("Expected a <name> constructor", path);
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
                        LiteralExpression(SyntaxKind.StringLiteralExpression, Literal($"Expected a {constructorName} constructor"))
                    ),
                    Token(SyntaxKind.CommaToken),
                    Argument(IdentifierName("path")),
                ]))
            )
        );

        
        var decodeFields = WriteDecodeFields(Decl);

        StatementSyntax[] ifBody = [
	        argsDeclaration,
	        kwargsDeclaration,
	        ..decodeFields.Statements,
        ];
        
        // if (...) { ... } else { ... }
        var ifStatement = IfStatement(
            ifCondition,
            Block(ifBody),
            ElseClause(Block(throwStatement))
        );


        return Block(ifStatement);
	}
}
