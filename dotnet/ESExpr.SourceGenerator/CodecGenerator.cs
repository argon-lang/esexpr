using System;
using System.Collections.Generic;
using System.Diagnostics.CodeAnalysis;
using System.Linq;
using System.Linq.Expressions;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using static Microsoft.CodeAnalysis.CSharp.SyntaxFactory;
using static ESExpr.SourceGenerator.GenUtils;

namespace ESExpr.SourceGenerator;

internal abstract class CodecGenerator<TDecl> where TDecl : BaseTypeDeclarationSyntax {

	public required GeneratorExecutionContext Context { get; init; }
	public required SemanticModel SemanticModel { get; init; }
	
	public required CodecOverrideHandler CodecOverrideHandler { get; init; }
	public required TDecl Decl { get; init; }

	protected abstract ExpressionSyntax GenerateTagsBody();
	protected abstract BlockSyntax GenerateEncodeBody();
	protected abstract BlockSyntax GenerateDecodeBody();

	
	
	public void GenerateCodecClass() {
		var syntaxTree = CompilationUnit()
			.AddUsings(Decl.SyntaxTree.GetCompilationUnitRoot().Usings.ToArray());
			
		var outerType = GetDeclarationAsType(Decl);
		
		var members = new List<MemberDeclarationSyntax>();

		{
			if(Decl is TypeDeclarationSyntax { TypeParameterList: {} typeParams }) {
				var constructor = ConstructorDeclaration("Codec")
					.WithParameterList(ParameterList(SeparatedList(
						typeParams.Parameters.Select(tp => 
							Parameter(Identifier(PascalCaseToCamelCase(tp.Identifier.Text) + "Codec"))
								.WithType(ESExprCodecType(IdentifierName(tp.Identifier.Text)))
						)
					)))
					.WithBody(Block(List(
						typeParams.Parameters.Select(tp => {
							var fieldName = PascalCaseToCamelCase(tp.Identifier.Text) + "Codec";
							
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

				foreach(var tp in typeParams.Parameters) {
					members.Add(
						FieldDeclaration(
							VariableDeclaration(ESExprCodecType(IdentifierName(tp.Identifier.Text)))
								.WithVariables(SeparatedList(new VariableDeclaratorSyntax[] {
									VariableDeclarator(Identifier(PascalCaseToCamelCase(tp.Identifier.Text) + "Codec")),
								}))
						)
							.AddModifiers(Token(SyntaxKind.PrivateKeyword), Token(SyntaxKind.ReadOnlyKeyword))
					);
				}
			}
		}
		
        var tagsProp = PropertyDeclaration(
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
	                GenericName(Identifier("ISet"))
		                .WithTypeArgumentList(
			                TypeArgumentList(SingletonSeparatedList<TypeSyntax>(
				                ESExprTagType
			                ))
		                )
	            ),
                Identifier("Tags")
            )
            .AddModifiers(Token(SyntaxKind.PublicKeyword))
            .WithExpressionBody(
                ArrowExpressionClause(
                    GenerateTagsBody()
                )
            )
            .WithSemicolonToken(Token(SyntaxKind.SemicolonToken));
        members.Add(tagsProp);

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
		
		var codecClass = ClassDeclaration("Codec")
			.WithBaseList(BaseList(
				Token(SyntaxKind.ColonToken),
				SeparatedList(new BaseTypeSyntax[] {
					SimpleBaseType(ESExprCodecType(outerType)),
				})
			))
			.AddModifiers(Token(SyntaxKind.PublicKeyword), Token(SyntaxKind.SealedKeyword))
			.AddMembers(members.ToArray());
		
		var outerClass =
			RecordDeclaration(
				Token(SyntaxKind.RecordKeyword),
				Decl.Identifier.ToString()
			)
			.AddModifiers(Token(SyntaxKind.PartialKeyword))
			.WithOpenBraceToken(Token(SyntaxKind.OpenBraceToken))
			.WithCloseBraceToken(Token(SyntaxKind.CloseBraceToken))
			.AddMembers(codecClass);

		{
			if(Decl is TypeDeclarationSyntax { TypeParameterList: { } typeParams }) {
				outerClass = outerClass.AddTypeParameterListParameters(typeParams.Parameters.ToArray());
			}
		}

		// Generate the namespace with the class
		var nsName = NameFromNamespaceNodes(Decl.Parent);

		string fileNamePrefix;
		if(nsName is not null) {
			var namespaceDeclaration = NamespaceDeclaration(nsName)
				.AddMembers(outerClass);

			syntaxTree = syntaxTree.AddMembers(namespaceDeclaration);
			fileNamePrefix = nsName.ToString() + "." + Decl.Identifier.ToString();
		}
		else {
			syntaxTree = syntaxTree.AddMembers(outerClass);
			fileNamePrefix = Decl.Identifier.ToString();
		}

		syntaxTree = syntaxTree.NormalizeWhitespace();

		
		
		Context.AddSource($"{fileNamePrefix}.ESExprCodec.g.cs", syntaxTree.GetText(Encoding.UTF8));
	}

	protected BlockSyntax WriteEncodeFields(RecordDeclarationSyntax typeDecl, ExpressionSyntax valueExpr) {
		var constructorName = GetConstructorName(typeDecl);
		
		
		// var args = new global::System.Collections.Generic.List<global::ESExpr.Runtime.ESExpr>();
		var argsDeclaration = LocalDeclarationStatement(
			VariableDeclaration(IdentifierName("var"))
				.WithVariables(
					SingletonSeparatedList(
						VariableDeclarator(Identifier("args"))
							.WithInitializer(
								EqualsValueClause(
									ObjectCreationExpression(ListType(ESExprType))
										.WithArgumentList(ArgumentList())
								)
							)
					)
				)
		);

		// var kwargs = new global::System.Collections.Generic.Dictionary<string, global::ESExpr.Runtime.ESExpr>();
		var kwargsDeclaration = LocalDeclarationStatement(
			VariableDeclaration(IdentifierName("var"))
				.WithVariables(
					SingletonSeparatedList(
						VariableDeclarator(Identifier("kwargs"))
							.WithInitializer(
								EqualsValueClause(
									ObjectCreationExpression(DictionaryType(StringType, ESExprType))
										.WithArgumentList(ArgumentList())
								)
							)
					)
				)
		);

		// return new global::ESExpr.Runtime.ESExpr.Constructor(name, args, kwargs);
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
							Argument(IdentifierName("args")),
							Argument(IdentifierName("kwargs")),
						])
					)
				)
		);
		
		var stmts = new List<StatementSyntax> {
			argsDeclaration,
			kwargsDeclaration,
		};

		bool hasDict = false;
		bool hasVararg = false;
		

		foreach(var prop in typeDecl.Members.OfType<PropertyDeclarationSyntax>()) {
			var propType = SemanticModel.GetTypeInfo(prop.Type).Type;
			if(propType == null) {
				throw new Exception("Could not resolve type.");
			}
			
			
			var propertyValue = MemberAccessExpression(
				SyntaxKind.SimpleMemberAccessExpression,
				valueExpr,
				IdentifierName(prop.Identifier.Text)
			);

			if(IsKeyword(prop) is {} keyword) {
				if(hasDict) {
					Context.ReportDiagnostic(Diagnostic.Create(
						Errors.KeywordAfterDict,
						prop.Identifier.GetLocation(),
						new object?[] {}
					));
				}
				
				if(IsOptional(prop)) {
					
					
					var encodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetOptionalCodecExpr(propType),
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
				}
				else {
					var encodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(propType),
							IdentifierName("Encode")
						),
						ArgumentList(SeparatedList([
							Argument(propertyValue),
						]))
					);

					if(IsDefaultValue(prop) is { } defaultValue) {
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
											IdentifierName("encodedExpr"),
											IdentifierName("Equals")
										),
										ArgumentList(SeparatedList([
											Argument(defaultValue),
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
				}
			}
			else if(IsVararg(prop)) {
				if(hasVararg) {
					Context.ReportDiagnostic(Diagnostic.Create(
						Errors.MultipleVarargs,
						prop.Identifier.GetLocation(),
						new object?[] {}
					));
				}
				
				hasVararg = true;
				
				var encodedExpr = InvocationExpression(
					MemberAccessExpression(
						SyntaxKind.SimpleMemberAccessExpression,
						GetVarargCodecExpr(propType),
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
			}
			else if(IsDict(prop)) {
				if(hasDict) {
					Context.ReportDiagnostic(Diagnostic.Create(
						Errors.MultipleDict,
						prop.Identifier.GetLocation(),
						new object?[] {}
					));
				}
				
				hasDict = true;
				
				
				var encodedExpr = InvocationExpression(
					MemberAccessExpression(
						SyntaxKind.SimpleMemberAccessExpression,
						GetDictCodecExpr(propType),
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
			}
			else {
				if(hasVararg) {
					Context.ReportDiagnostic(Diagnostic.Create(
						Errors.PositionalAfterVararg,
						prop.Identifier.GetLocation(),
						new object?[] {}
					));
				}
				
				var encodedExpr = InvocationExpression(
					MemberAccessExpression(
						SyntaxKind.SimpleMemberAccessExpression,
						GetCodecExpr(propType),
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
			}
		}
		
		stmts.Add(returnStatement);
		
		return Block(stmts);
	}

	protected BlockSyntax WriteDecodeFields(RecordDeclarationSyntax typeDecl) {
		var constructorName = GetConstructorName(typeDecl);
		
		var stmts = new List<StatementSyntax>();

		var fieldInits = new List<ExpressionSyntax>();

		int positionalIndex = 0;

		foreach(var prop in typeDecl.Members.OfType<PropertyDeclarationSyntax>()) {
			var propType = SemanticModel.GetTypeInfo(prop.Type).Type;
			if(propType == null) {
				throw new Exception("Could not resolve type.");
			}
			

			var localName = "local_" + prop.Identifier.Text;
			
			if(IsKeyword(prop) is {} keyword) {
				stmts.Add(LocalDeclarationStatement(
					VariableDeclaration(ConvertTypeSymbolToTypeSyntax(propType))
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
				
				ExpressionSyntax decodedExpr;
				BlockSyntax falseBody;

				if(IsOptional(prop)) {
					decodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetOptionalCodecExpr(propType),
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
							GetOptionalCodecExpr(propType),
							IdentifierName("DecodeOptional")
						),
						ArgumentList(SeparatedList(new[] {
							Argument(LiteralExpression(SyntaxKind.NullLiteralExpression)),
							Argument(pathExpr),
						}))
					);
					
					falseBody = Block(
						ExpressionStatement(AssignmentExpression(
							SyntaxKind.SimpleAssignmentExpression,
							IdentifierName(localName),
							emptyExpr
						))
					);
				}
				else {
					decodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(propType),
							IdentifierName("Decode")
						),
						ArgumentList(SeparatedList(new[] {
							Argument(IdentifierName("kwargExpr")),
							Argument(pathExpr),
						}))
					);
					
					if(IsDefaultValue(prop) is {} defaultValue) {
						falseBody = Block(
							ExpressionStatement(AssignmentExpression(
								SyntaxKind.SimpleAssignmentExpression,
								IdentifierName(localName),
								defaultValue
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
				}
				
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
			else if(IsVararg(prop)) {
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
						GetVarargCodecExpr(propType),
						IdentifierName("DecodeVararg")
					),
					ArgumentList(SeparatedList([
						Argument(IdentifierName("args")),
						Argument(pathExpr),
					]))
				);
			
				stmts.Add(LocalDeclarationStatement(
					VariableDeclaration(ConvertTypeSymbolToTypeSyntax(propType))
						.WithVariables(
							SingletonSeparatedList(
								VariableDeclarator(Identifier(localName))
									.WithInitializer(
										EqualsValueClause(decodedExpr)
									)
							)
						)
				));
				
				
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
								ArgumentList(SeparatedList([
									Argument(LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(0))),
									Argument(LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(0))),
								]))
							)
					)
				);
				stmts.Add(sliceStatement);
			}
			else if(IsDict(prop)) {
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
						GetDictCodecExpr(propType),
						IdentifierName("DecodeDict")
					),
					ArgumentList(SeparatedList([
						Argument(IdentifierName("kwargs")),
						Argument(pathExpr),
					]))
				);
			
				stmts.Add(LocalDeclarationStatement(
					VariableDeclaration(ConvertTypeSymbolToTypeSyntax(propType))
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
			}
			else {
				var codecExpr = GetCodecExpr(propType);
				
				
				var ifCondition = BinaryExpression(
					SyntaxKind.EqualsExpression,
					MemberAccessExpression(
						SyntaxKind.SimpleMemberAccessExpression,
						IdentifierName("args"),
						IdentifierName("Count")),
					LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(0))
				);
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
					VariableDeclaration(ConvertTypeSymbolToTypeSyntax(propType))
						.WithVariables(
							SingletonSeparatedList(
								VariableDeclarator(Identifier(localName))
									.WithInitializer(
										EqualsValueClause(decodedExpr)
									)
							)
						)
				));
			
				
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
				stmts.Add(sliceStatement);
			}
			
				
			fieldInits.Add(AssignmentExpression(
				SyntaxKind.SimpleAssignmentExpression,
				IdentifierName(prop.Identifier.Text),
				IdentifierName(localName)
			));
		}

		var objExpr = ObjectCreationExpression(GetDeclarationAsType(typeDecl))
			.WithInitializer(InitializerExpression(
				SyntaxKind.ObjectInitializerExpression,
				SeparatedList(fieldInits)
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
					SeparatedList([ elementType ])
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
	
	protected TypeSyntax ListType(TypeSyntax elementType) =>
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
				Identifier("List"),
				TypeArgumentList(
					SeparatedList([ elementType ])
				)
			)
			
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
					SeparatedList([ keyType, valueType ])
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
					SeparatedList([ elementType ])
				)
			)
		);
	
	protected static TypeSyntax GetDeclarationAsType(BaseTypeDeclarationSyntax decl) {
		if(decl is TypeDeclarationSyntax { TypeParameterList: not null } typeDecl) {
			return GenericName(
				Identifier(decl.Identifier.ToString()),
				TypeArgumentList(
					SeparatedList(
						typeDecl.TypeParameterList.Parameters.Select(tp => {
							TypeSyntax tpType = IdentifierName(tp.Identifier.ToString());
							return tpType;
						})
					)
				)
			);
		}
		else {
			return IdentifierName(decl.Identifier.ToString());	
		}
	}

	protected static NameSyntax? NameFromNamespaceNodes(SyntaxNode? syntax) {
		if(syntax is not BaseNamespaceDeclarationSyntax ns) {
			return null;
		}

		var parentNS = NameFromNamespaceNodes(ns.Parent);

		return MergeNames(parentNS, ns.Name);
	}

	protected static NameSyntax MergeNames(NameSyntax? a, NameSyntax b) {
		if(a == null) {
			return b;
		}

		return b switch {
			null => a,
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


	protected ExpressionSyntax GetCodecExpr(ITypeSymbol t) =>
		GetCodecLikeExpr(t, "IESExprCodec", "Codec");

	protected ExpressionSyntax GetOptionalCodecExpr(ITypeSymbol t) =>
		GetCodecLikeExpr(t, "IOptionalValueCodec", "OptionalValueCodec");

	protected ExpressionSyntax GetVarargCodecExpr(ITypeSymbol t) =>
		GetCodecLikeExpr(t, "IVarargCodec", "VarargCodec");

	protected ExpressionSyntax GetDictCodecExpr(ITypeSymbol t) =>
		GetCodecLikeExpr(t, "IDictCodec", "DictCodec");
	
	protected ExpressionSyntax GetCodecLikeExpr(ITypeSymbol t, string codecTypeName, string nestedClassName) {
		var codecType = Context.Compilation.GetTypeByMetadataName($"ESExpr.Runtime.{codecTypeName}`1");
		if(codecType == null) {
			throw new Exception($"Could not find {codecTypeName}");
		}

		TypeSyntax concreteCodecType;
		IEnumerable<ITypeSymbol> typeArgs;
		
		var overrideCodec = CodecOverrideHandler.GetOverriddenCodec(codecType.Construct(t));
		if(overrideCodec != null) {
			concreteCodecType = ConvertTypeSymbolToTypeSyntax(overrideCodec);
			typeArgs = overrideCodec switch {
				INamedTypeSymbol { IsGenericType: true } named => named.TypeArguments,
				_ => [],
			};
		}
		else if(t.TypeKind == TypeKind.Enum) {
			return MemberAccessExpression(
				SyntaxKind.SimpleMemberAccessExpression,
				QualifiedName(
					QualifiedName(
						AliasQualifiedName(
							IdentifierName(Token(SyntaxKind.GlobalKeyword)),
							IdentifierName("ESExpr")
						),
						IdentifierName("Runtime")
					),
					GenericName(
						Identifier("SimpleEnumCodec"),
						TypeArgumentList(
							SeparatedList([ ConvertTypeSymbolToTypeSyntax(t) ])
						)
					)
				),
				IdentifierName("Instance")
			);
		}
		else if(t is ITypeParameterSymbol) {
			return MemberAccessExpression(
				SyntaxKind.SimpleMemberAccessExpression,
				ThisExpression(),
				IdentifierName(PascalCaseToCamelCase(t.Name) + "Codec")
			);
		}
		else {
			var tSyntax = ConvertTypeSymbolToTypeSyntax(t);
			if(tSyntax is NameSyntax typeName) {
				concreteCodecType = QualifiedName(
					(NameSyntax)ConvertTypeSymbolToTypeSyntax(t),
					IdentifierName(nestedClassName)
				);
				typeArgs = t switch {
					INamedTypeSymbol { IsGenericType: true } named => named.TypeArguments,
					_ => [],
				};
			}
			else {
				throw new AbortGenerationException(
					Diagnostic.Create(
						Errors.CouldNotDetermineCodec,
						Decl.Identifier.GetLocation(),
						codecTypeName,
						tSyntax
					)
				);
			}
		}

		var args = typeArgs.Select(arg =>
			Argument(GetCodecExpr(arg))
		);

		return ObjectCreationExpression(concreteCodecType)
			.WithArgumentList(ArgumentList(SeparatedList(args)));
	}
	
	
	
	protected static TypeSyntax ConvertTypeSymbolToTypeSyntax(ITypeSymbol typeSymbol) {
		switch (typeSymbol) {
			case INamedTypeSymbol namedTypeSymbol:
			{
				SimpleNameSyntax name;
				if(namedTypeSymbol.IsGenericType) {
					var genericArguments = namedTypeSymbol.TypeArguments.Select(ConvertTypeSymbolToTypeSyntax);
					name = GenericName(
						Identifier(namedTypeSymbol.Name),
						TypeArgumentList(SeparatedList(genericArguments))
					);
				}
				else {
					name = IdentifierName(namedTypeSymbol.Name);
				}
				
				if(namedTypeSymbol.ContainingType is {} outerType) {
					return QualifiedName(
						(NameSyntax)ConvertTypeSymbolToTypeSyntax(outerType),
						name
					);
				}
				else if(namedTypeSymbol.ContainingNamespace is { } outerNamespace) {
					return GetNamespaceMemberSyntax(outerNamespace, name);
				}
				else {
					throw new Exception("Could not determine parent of type " + typeSymbol);
				}
			}

			case IArrayTypeSymbol arrayTypeSymbol:
				var elementTypeSyntax = ConvertTypeSymbolToTypeSyntax(arrayTypeSymbol.ElementType);
				return ArrayType(elementTypeSyntax, SingletonList(ArrayRankSpecifier()));

			case IPointerTypeSymbol pointerTypeSymbol:
				var pointedAtTypeSyntax = ConvertTypeSymbolToTypeSyntax(pointerTypeSymbol.PointedAtType);
				return PointerType(pointedAtTypeSyntax);

			case ITypeParameterSymbol typeParameterSymbol:
				return IdentifierName(typeParameterSymbol.Name);

			default:
				return ParseTypeName(typeSymbol.ToDisplayString(SymbolDisplayFormat.FullyQualifiedFormat));
		}
	}

	private static NameSyntax GetNamespaceMemberSyntax(INamespaceSymbol ns, SimpleNameSyntax memberName) {
		if(ns.ContainingNamespace is { } parentNamespace) {
			return QualifiedName(GetNamespaceMemberSyntax(parentNamespace, IdentifierName(ns.Name)), memberName);
		}
		else {
			return AliasQualifiedName(
				IdentifierName(Token(SyntaxKind.GlobalKeyword)),
				memberName
			);
		}
	}
	
	protected string GetConstructorName(TypeDeclarationSyntax decl) {
		if(
			GetAttribute(decl, "ESExpr.Runtime.ConstructorAttribute", SemanticModel) is { ArgumentList.Arguments: var args } &&
			args.Count == 1 &&
			args[0].Expression is LiteralExpressionSyntax value
		) {
			return value.Token.ValueText;
		}
		else {
			return NameToKebabCase(decl.Identifier.Text);			
		}
	}

	private bool IsVararg(PropertyDeclarationSyntax decl) =>
		HasAttribute(decl, "ESExpr.Runtime.VarargAttribute", SemanticModel);

	private bool IsDict(PropertyDeclarationSyntax decl) =>
		HasAttribute(decl, "ESExpr.Runtime.DictAttribute", SemanticModel);

	private bool IsOptional(PropertyDeclarationSyntax decl) =>
		HasAttribute(decl, "ESExpr.Runtime.OptionalAttribute", SemanticModel);

	private ExpressionSyntax? IsDefaultValue(PropertyDeclarationSyntax decl) {
		var attr = GetAttribute(decl, "ESExpr.Runtime.DefaultValueAttribute", SemanticModel);
		if(
			attr is { ArgumentList.Arguments: var args } &&
			args.Count == 1 &&
			args[0].Expression is LiteralExpressionSyntax value
		) {
			return ParseExpression(value.Token.ValueText);
		}
		else if(decl.Initializer is { Value: var initValue }) {
			return initValue;
		}
		else {
			return null;
		}
	}

	private string? IsKeyword(PropertyDeclarationSyntax decl) {
		var attr = GetAttribute(decl, "ESExpr.Runtime.KeywordAttribute", SemanticModel);
		if(attr == null) {
			return null;
		}
		
		if(
			attr is { ArgumentList.Arguments: var args } &&
			args.Count == 1 &&
			args[0].Expression is LiteralExpressionSyntax value
		) {
			return value.Token.ValueText;
		}
		else {
			return NameToKebabCase(decl.Identifier.Text);			
		}
	}

	protected bool IsInlineValue(RecordDeclarationSyntax decl) =>
		HasAttribute(decl, "ESExpr.Runtime.InlineValueAttribute", SemanticModel);

	private string NameToKebabCase(string name) =>
		string.Join(
			"-",
			Regex.Split(name, "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])_(?=[0-9])")
				.Select(s => s.ToLowerInvariant())
		);

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
