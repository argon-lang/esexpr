package dev.argon.esexpr.generator;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.List;

class MetadataCache {
	public MetadataCache(ProcessingEnvironment env) {
		this.env = env;
	}

	private final ProcessingEnvironment env;
	private List<CodecOverride> codecOverrides = null;

	public List<CodecOverride> getCodecOverrides() {
		if(codecOverrides == null) {
			codecOverrides = CodecOverride.scan(env);
		}

		return codecOverrides;
	}

}
