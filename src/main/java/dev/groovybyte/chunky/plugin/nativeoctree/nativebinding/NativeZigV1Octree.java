package dev.groovybyte.chunky.plugin.nativeoctree.nativebinding;

import it.unimi.dsi.fastutil.ints.IntIntMutablePair;
import se.llbit.chunky.chunk.BlockPalette;
import se.llbit.chunky.world.Material;
import se.llbit.math.Octree;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;

public class NativeZigV1Octree implements Octree.OctreeImplementation {

	final static ZigLib lib = new ZigLib("chunky_native_zig");
	final static Cleaner cleaner = Cleaner.create();

	record State(
		MemorySession session,
		MemoryAddress address,
		byte depth
	) implements Runnable {
		State(byte depth) {
			this(
				MemorySession.openShared(cleaner),
				lib.createOctree(depth),
				depth
			);
		}

		@Override
		public void run() {
			System.out.println("closing octree @" + address.toRawLongValue());
			lib.destroyOctree(address);
			session.close();
		}
	}

	final State state;
	public NativeZigV1Octree(byte depth) {
		state = new State(depth);
		cleaner.register(this, state);
	}

	//	public void importFile(File file) throws IOException {
	//		try(var fchan = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
	//			try(MemorySession session = MemorySession.openConfined()) {
	//				var sgmt = fchan.map(FileChannel.MapMode.READ_ONLY, 0, fchan.size(), session);
	//				importData(sgmt.asSlice(4));
	//			}
	//		}
	//	}

	public void importData(MemorySegment segment) {
		lib.importOctree(state.address, segment);
	}

	public void debugContent() {
		ByteBuffer buf = lib.getOctreeData(state.session, state.address);
		System.out.print("\t[");
		for(int i = 0; buf.hasRemaining() && i < 32; i++) {
			int d = buf.getInt();
			String type = switch(d >>> 28) {
				case 0b1000 -> "data";
				case 0b0010 -> "abs_ptr";
				case 0b0011 -> "rel_ptr";
				default -> "invalid";
			};
			System.out.print(type + "=" + Integer.toUnsignedString(d&((1<<28) - 1), 16) + ", ");
		}
		if(buf.hasRemaining())
			System.out.print("...");
		System.out.println("]");
	}

	//	public void update(int x, int y, int z, short[] data) {
	//		try(var session = MemorySession.openConfined()) {
	//			var memSgmt = session.allocateArray(ValueLayout.JAVA_SHORT, data);
	//			lib.updateOctree(address, x, y, z, memSgmt);
	//		}
	//	}

	@Override
	public int getDepth() {
		return state.depth;
	}

	@Override
	public long nodeCount() {
		return lib.getNodeCount(state.address);
	}

	int getDataAt(int x, int y, int z) {
		return lib.getNode(state.address, x, y, z);
	}

	@Override
	public Material getMaterial(int x, int y, int z, BlockPalette palette) {
		return palette.get(getDataAt(x, y, z));
	}

	@Override
	public void getWithLevel(IntIntMutablePair outTypeAndLevel, int x, int y, int z) {
		lib.getNodeWithLevel(state.address, outTypeAndLevel, x, y, z);
	}

	@Override
	public void set(int type, int x, int y, int z) {
		lib.setNode(state.address, x, y, z, type);
	}

	@Override
	public void store(DataOutputStream output) throws IOException {
		output.writeInt(state.depth);
		// TODO
		throw new UnsupportedOperationException();
	}

	//	default void setCube(int cubeDepth, int[] types, int x, int y, int z) {
	//		// Default implementation sets block one by one
	//		int size = 1 << cubeDepth;
	//		assert x % size == 0;
	//		assert y % size == 0;
	//		assert z % size == 0;
	//		for(int localZ = 0; localZ < size; ++localZ) {
	//			for(int localY = 0; localY < size; ++localY) {
	//				for(int localX = 0; localX < size; ++localX) {
	//					int globalX = x + localX;
	//					int globalY = y + localY;
	//					int globalZ = z + localZ;
	//					int index = (localZ * size + localY) * size + localX;
	//					set(types[index], globalX, globalY, globalZ);
	//				}
	//			}
	//		}
	//	}

	public static void initImplementation() {
		Octree.addImplementationFactory("NATIVE_ZIGv1", new Octree.ImplementationFactory() {
			/**
			 * Constructs a new mutable octree instance with an initialized root node.
			 */
			@Override
			public Octree.OctreeImplementation create(
				int depth
			) {
				assert depth >= 0 && depth < 0xFF;
				return new NativeZigV1Octree((byte) depth);
			}

			@Override
			public Octree.OctreeImplementation load(
				DataInputStream in
			) throws IOException {
				// TODO
				throw new UnsupportedOperationException();
			}

			@Override
			public Octree.OctreeImplementation loadWithNodeCount(
				long nodeCount, DataInputStream in
			) throws IOException {
				// TODO
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean isOfType(Octree.OctreeImplementation implementation) {
				return implementation instanceof NativeZigV1Octree;
			}

			@Override
			public String getDescription() {
				return "Native memory efficient octree implementation in Zig using off-heap (native) memory.";
				// which works with (almost) any amount of nodes
			}
		});
	}

	@Override
	public Octree.NodeId getRoot() {
		// this is stupid, I will not do this
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isBranch(Octree.NodeId node) {
		// this is stupid, I will not do this
		throw new UnsupportedOperationException();
	}

	@Override
	public Octree.NodeId getChild(Octree.NodeId parent, int childNo) {
		// this is stupid, I will not do this
		throw new UnsupportedOperationException();
	}

	@Override
	public int getType(Octree.NodeId node) {
		// this is stupid, I will not do this
		throw new UnsupportedOperationException();
	}
}
