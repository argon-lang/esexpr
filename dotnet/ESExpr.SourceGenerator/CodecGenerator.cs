using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;
using System.Linq.Expressions;
using System.Text;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using static Microsoft.CodeAnalysis.CSharp.SyntaxFactory;

namespace ESExpr.SourceGenerator;

internal abstract class CodecGenerator<TTypeModel> : ICodecGenerator where TTypeModel : TypeSourceModelDeclaration {

	public required SourceProductionContext Context { get; init; }
	public required TypeInfoHandler TypeInfoHandler { get; init; }

	public required TTypeModel TypeModel { get; init; }

	protected abstract BlockSyntax GenerateIsEqualBody();
	protected abstract BlockSyntax GenerateEncodeBody();
	protected abstract BlockSyntax GenerateDecodeBody();


	public void Generate() {
		var syntaxTree = CompilationUnit()
			.AddUsings(TypeModel.Usings.Select(u => u.Syntax).ToArray());

		TypeSyntax outerType = IdentifierName(TypeModel.TypeName);

		var members = new List<MemberDeclarationSyntax>();

		{
			if(TypeModel.TypeParameters.Count != 0) {
				var constructor = ConstructorDeclaration("Codec")
					.WithParameterList(ParameterList(SeparatedList(
						TypeModel.TypeParameters.Select(tp =>
							Parameter(Identifier(PascalCaseToCamelCase(tp.Syntax.Identifier.Text) + "Codec"))
								.WithType(ESExprCodecType(IdentifierName(tp.Syntax.Identifier.Text)))
						)
					)))
					.WithBody(Block(List(
						TypeModel.TypeParameters.Select(tp => {
							var fieldName = PascalCaseToCamelCase(tp.Syntax.Identifier.Text) + "Codec";

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

				foreach(var tp in TypeModel.TypeParameters) {
					members.Add(
						FieldDeclaration(
							VariableDeclaration(ESExprCodecType(IdentifierName(tp.Syntax.Identifier.Text)))
								.WithVariables(SeparatedList(new VariableDeclaratorSyntax[] {
									VariableDeclarator(Identifier(PascalCaseToCamelCase(tp.Syntax.Identifier.Text) + "Codec")),
								}))
						)
							.AddModifiers(Token(SyntaxKind.PrivateKeyword), Token(SyntaxKind.ReadOnlyKeyword))
					);
				}

				outerType = GenericName(TypeModel.TypeName)
					.AddTypeArgumentListArguments(
						TypeModel.TypeParameters
							.Select(tp => IdentifierName(tp.Syntax.Identifier.Text))
							.ToArray<TypeSyntax>()
					);
			}
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
				TypeModel.TypeName
			)
			.AddModifiers(Token(SyntaxKind.PartialKeyword))
			.WithOpenBraceToken(Token(SyntaxKind.OpenBraceToken))
			.WithCloseBraceToken(Token(SyntaxKind.CloseBraceToken))
			.AddMembers(codecClass);

		{
			if(TypeModel.TypeParameters.Count != 0) {
				outerClass = outerClass.AddTypeParameterListParameters(TypeModel.TypeParameters.Select(tp => tp.Syntax).ToArray());
			}
		}

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
		TagsToExpr(GetTags(TypeModel.SourceModelType, ImmutableHashSet<SourceModelType>.Empty));

	private ESExprTagSet GetTags(SourceModelType type, ImmutableHashSet<SourceModelType> seenTypes) {
		var typeTags = TypeInfoHandler.GetTags(type);
		if(typeTags == null) {
			throw new Exception("Could not get tags for type: " + type);
		}
		
		return typeTags.unionWithTypes.Aggregate(
			typeTags.tags,
			(tags, t) =>
				tags.Union(GetTags(t, seenTypes.Add(type)))
		);
	}

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
	
	protected BlockSyntax WriteIsEqualFields(VList<SourceModelField> fields, ExpressionSyntax aExpr, ExpressionSyntax bExpr) {
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

			
			ExpressionSyntax codecExpr;
			if(field.IsVararg) {
				codecExpr = GetVarargCodecExpr(field.Type);
			}
			else if(field.IsDict) {
				codecExpr = GetDictCodecExpr(field.Type);
			}
			else if(field.IsOptional) {
				codecExpr = GetOptionalCodecExpr(field.Type);
			}
			else {
				codecExpr = GetCodecExpr(field.Type);
			}

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

	protected BlockSyntax WriteEncodeFields(string constructorName, VList<SourceModelField> fields, ExpressionSyntax valueExpr) {
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


		foreach(var field in fields) {
			var propertyValue = MemberAccessExpression(
				SyntaxKind.SimpleMemberAccessExpression,
				valueExpr,
				IdentifierName(field.Name)
			);

			if(field.IsKeyword is { } keyword) {
				if(hasDict) {
					Context.ReportDiagnostic(Diagnostic.Create(
						Errors.KeywordAfterDict,
						field.Location,
						new object?[] { }
					));
				}

				if(field.IsOptional) {


					var encodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetOptionalCodecExpr(field.Type),
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
							GetCodecExpr(field.Type),
							IdentifierName("Encode")
						),
						ArgumentList(SeparatedList([
							Argument(propertyValue),
						]))
					);

					if(field.DefaultValue is { } defaultValue) {
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
											GetCodecExpr(field.Type),
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
				}
			}
			else if(field.IsVararg) {
				if(hasVararg) {
					Context.ReportDiagnostic(Diagnostic.Create(
						Errors.MultipleVarargs,
						field.Location,
						new object?[] { }
					));
				}

				hasVararg = true;

				var encodedExpr = InvocationExpression(
					MemberAccessExpression(
						SyntaxKind.SimpleMemberAccessExpression,
						GetVarargCodecExpr(field.Type),
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
			else if(field.IsDict) {
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
						GetDictCodecExpr(field.Type),
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
						field.Location,
						new object?[] { }
					));
				}

				if(field.IsOptional) {
					var encodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetOptionalCodecExpr(field.Type),
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
				}
				else if(field.DefaultValue is { } defaultValue) {
					var codecExpr = GetCodecExpr(field.Type);

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
				}
				else {
					var encodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetCodecExpr(field.Type),
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
		}

		stmts.Add(returnStatement);

		return Block(stmts);
	}

	protected BlockSyntax WriteDecodeFields(string constructorName, VList<SourceModelField> fields, TypeSyntax objectType) {
		var stmts = new List<StatementSyntax>();

		var fieldInits = new List<ExpressionSyntax>();

		int positionalIndex = 0;

		foreach(var field in fields) {
			var localName = "local_" + field.Name;

			if(field.IsKeyword is { } keyword) {
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

				ExpressionSyntax decodedExpr;
				BlockSyntax falseBody;

				if(field.IsOptional) {
					decodedExpr = InvocationExpression(
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							GetOptionalCodecExpr(field.Type),
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
							GetOptionalCodecExpr(field.Type),
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
							GetCodecExpr(field.Type),
							IdentifierName("Decode")
						),
						ArgumentList(SeparatedList(new[] {
							Argument(IdentifierName("kwargExpr")),
							Argument(pathExpr),
						}))
					);

					if(field.DefaultValue is { } defaultValue) {
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
			else if(field.IsVararg) {
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
						GetVarargCodecExpr(field.Type),
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
			else if(field.IsDict) {
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
						GetDictCodecExpr(field.Type),
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
			}
			else {
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

				if(field.IsOptional) {
					ExpressionSyntax DecodeOptionalExpr(ExpressionSyntax expr) =>
						AssignmentExpression(
							SyntaxKind.SimpleAssignmentExpression,
							IdentifierName(localName),
							InvocationExpression(
								MemberAccessExpression(
									SyntaxKind.SimpleMemberAccessExpression,
									GetOptionalCodecExpr(field.Type),
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
									GetOptionalCodecExpr(field.Type),
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
				}
				else if(field.DefaultValue is { } defaultValue) {
					var codecExpr = GetCodecExpr(field.Type);

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
				}
				else {
					var ifCondition = BinaryExpression(
						SyntaxKind.EqualsExpression,
						MemberAccessExpression(
							SyntaxKind.SimpleMemberAccessExpression,
							IdentifierName("args"),
							IdentifierName("Count")),
						LiteralExpression(SyntaxKind.NumericLiteralExpression, Literal(0))
					);
					
					var codecExpr = GetCodecExpr(field.Type);

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
				}

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
					SeparatedList([elementType])
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

	protected static TypeSyntax GetDeclarationAsType(string name, VList<SourceModelSyntax<TypeParameterSyntax>> typeParameters) {
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


	protected ExpressionSyntax GetCodecExpr(SourceModelType t) =>
		GetCodecLikeExpr(t, "IESExprCodec", "Codec");

	protected ExpressionSyntax GetOptionalCodecExpr(SourceModelType t) =>
		GetCodecLikeExpr(t, "IOptionalValueCodec", "OptionalValueCodec");

	protected ExpressionSyntax GetVarargCodecExpr(SourceModelType t) =>
		GetCodecLikeExpr(t, "IVarargCodec", "VarargCodec");

	protected ExpressionSyntax GetDictCodecExpr(SourceModelType t) =>
		GetCodecLikeExpr(t, "IDictCodec", "DictCodec");

	protected ExpressionSyntax GetCodecLikeExpr(SourceModelType t, string codecTypeName, string nestedClassName) {
		SourceModelType codecType = new SourceModelType.NamedSymbol(VList.Of("ESExpr", "Runtime"), codecTypeName) {
			TypeArguments = VList.Of(t),
			IsEnum = false,
		};


		TypeSyntax concreteCodecType;
		IEnumerable<SourceModelType> typeArgs;

		var overrideCodec = TypeInfoHandler.GetOverriddenCodec(codecType);

		if(overrideCodec != null) {
			concreteCodecType = ConvertTypeToTypeSyntax(overrideCodec);
			typeArgs = overrideCodec switch {
				SourceModelType.NamedSymbol named => named.TypeArguments,
				_ => [],
			};
		}
		else if(t is SourceModelType.NamedSymbol { IsEnum: true }) {
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
							SeparatedList([ConvertTypeToTypeSyntax(t)])
						)
					)
				),
				IdentifierName("Instance")
			);
		}
		else if(t is SourceModelType.TypeParameter tp) {
			return MemberAccessExpression(
				SyntaxKind.SimpleMemberAccessExpression,
				ThisExpression(),
				IdentifierName(PascalCaseToCamelCase(tp.Name) + "Codec")
			);
		}
		else {
			var tSyntax = ConvertTypeToTypeSyntax(t);
			if(tSyntax is NameSyntax typeName) {
				concreteCodecType = QualifiedName(
					typeName,
					IdentifierName(nestedClassName)
				);
				typeArgs = t switch {
					SourceModelType.NamedSymbol named => named.TypeArguments,
					_ => [],
				};
			}
			else {
				throw new AbortGenerationException(
					Diagnostic.Create(
						Errors.CouldNotDetermineCodec,
						TypeModel.Location,
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

	protected static TypeSyntax ConvertTypeToTypeSyntax(SourceModelType t) {
		switch(t) {
			case SourceModelType.NamedSymbol namedTypeSymbol: {
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

				return GetNamespaceMemberSyntax(namedTypeSymbol.Namespace, namedTypeSymbol.Namespace.Count, name);
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

	private static NameSyntax GetNamespaceMemberSyntax(VList<string> ns, int nsPartCount, SimpleNameSyntax memberName) {
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
