package dev.argon.esexpr.generator.codegen;

import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.io.PrintWriter;

public class GeneratorBaseWriter {

	public GeneratorBaseWriter(PrintWriter writer) {
		this.writer = writer;
	}

	private final PrintWriter writer;
	private int indentLevel = 0;
	private boolean needsIndent = true;


	protected void indent() {
		indentLevel += 1;
	}

	protected void dedent() {
		indentLevel -= 1;
	}

	protected void print(CharSequence s) throws IOException {
		if(needsIndent) {
			for(int i = 0; i < indentLevel; ++i) {
				writer.print("\t");
			}

			needsIndent = false;
		}

		writer.print(s);
	}

	protected void println() throws IOException {
		writer.println();
		needsIndent = true;
	}

	protected void println(CharSequence s) throws IOException {
		print(s);
		println();
	}

	protected void printStringLiteral(String s) throws IOException {
		print("\"");
		print(StringEscapeUtils.escapeJava(s));
		print("\"");
	}
}
