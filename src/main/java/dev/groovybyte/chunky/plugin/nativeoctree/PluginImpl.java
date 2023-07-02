package dev.groovybyte.chunky.plugin.nativeoctree;

import dev.groovybyte.chunky.plugin.nativeoctree.nativebinding.NativeZigV1Octree;
import se.llbit.chunky.Plugin;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.ui.ChunkyFx;

public class PluginImpl implements Plugin {

	public static void main(String[] args) {
		Chunky.loadDefaultTextures();
		var chunky = new Chunky(ChunkyOptions.getDefaults());
		var plugin = new PluginImpl();
		plugin.attach(chunky);
		ChunkyFx.startChunkyUI(chunky);
	}

	@Override
	public void attach(Chunky chunky) {
		NativeZigV1Octree.initImplementation();
	}
}
