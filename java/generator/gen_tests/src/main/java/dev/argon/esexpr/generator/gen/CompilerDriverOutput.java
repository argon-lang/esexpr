package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.Constructor;
import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.TypeClassInstance;

@dev.argon.esexpr.ESExprCodecGen
public sealed interface CompilerDriverOutput<T_Fs, T_Ds> {
	@Constructor("file")
	record File<T_Fs, T_Ds>(
		T_Fs f
	) implements CompilerDriverOutput<T_Fs, T_Ds> {}
	@Constructor("directory")
	record Directory<T_Fs, T_Ds>(
		T_Ds dir
	) implements CompilerDriverOutput<T_Fs, T_Ds> {}

	@TypeClassInstance
	public static <T_Fs, T_Ds> ESExprCodec<CompilerDriverOutput<T_Fs, T_Ds>> codec(dev.argon.esexpr.ESExprCodec<T_Fs> fsCodec, dev.argon.esexpr.ESExprCodec<T_Ds> dsCodec) {
		return new CompilerDriverOutput_CodecImpl<T_Fs, T_Ds>(fsCodec, dsCodec);
	}
}
