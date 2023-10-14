package dev.groovybyte.chunky.plugin.nativeoctree.nativebinding;

import dev.groovybyte.chunky.plugin.nativeoctree.PluginImpl;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.NoSuchElementException;

public class LibraryBinding {

	final Arena arena;
	final Linker linker = Linker.nativeLinker();
	final SymbolLookup nativeLibrary;

	public LibraryBinding(
		String libraryName,
		ClassLoader classLoader
	) {
		this(libraryName, classLoader, Arena.global());
	}
	public LibraryBinding(
		String libraryName,
		ClassLoader classLoader,
		Arena arena
	) {
		this.arena = arena;
		SymbolLookup lib;
		try {
			System.loadLibrary(libraryName);
			lib = SymbolLookup.libraryLookup(libraryName, arena);
		} catch(UnsatisfiedLinkError ex) {
			try {
				var libraryFilename = System.mapLibraryName(libraryName);
				var jarPath = Path.of(PluginImpl.class.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.toURI());
				//				var nativeLibraryFile = jarPath.resolveSibling(libraryFilename);
				var libraryIS = classLoader.getResourceAsStream(libraryFilename);
				if(libraryIS == null) {
					throw new RuntimeException("Failed to find native library \"" + libraryFilename + "\"");
				}
				var nativeLibraryFile = Files.createTempFile(jarPath.getParent(), "nlib_", libraryFilename);
				Files.copy(libraryIS, nativeLibraryFile, StandardCopyOption.REPLACE_EXISTING);
				nativeLibraryFile.toFile().deleteOnExit();
				lib = SymbolLookup.libraryLookup(nativeLibraryFile, arena);
			} catch(URISyntaxException|IOException e) {
				throw new RuntimeException("Failed to extract native library", e);
			} catch(UnsatisfiedLinkError e) {
				throw new RuntimeException("Failed to load native library", ex);
			}
		}
		nativeLibrary = lib;
	}

	public MethodHandle findHandle(String functionName, FunctionDescriptor descriptor) {
		return linker.downcallHandle(
			nativeLibrary.find(functionName)
				.orElseThrow(() -> new NoSuchElementException("function not found \"" + functionName + "\"")),
			descriptor
		);
	}
}
