package dev.groovybyte.chunky.plugin.nativeoctree.nativebinding;

import it.unimi.dsi.fastutil.ints.IntIntMutablePair;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class ZigLib {

	/*
	const OctreeData = extern struct {
		capacity: u64,
		length: u64,
		pointer: *packed_octree.OctreeNode,
	};
	*/
	final static MemoryLayout OCTREE_DATA = MemoryLayout.structLayout(
		ValueLayout.JAVA_LONG.withName("capacity"),
		ValueLayout.JAVA_LONG.withName("length"),
		ValueLayout.ADDRESS.withName("pointer")
	);
	final static AddressLayout UNBOUNDED_POINTER = ValueLayout.ADDRESS
		.withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE));

	public ZigLib(String libraryName) {
		var lib = new LibraryBinding(libraryName, ZigLib.class.getClassLoader());
		version = lib.findHandle("version", FunctionDescriptor.of(UNBOUNDED_POINTER));
		getError = lib.findHandle("get_error", FunctionDescriptor.of(UNBOUNDED_POINTER));
		createOctree = lib.findHandle("create_octree", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
		destroyOctree = lib.findHandle("destroy_octree", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
		getNodeCount = lib.findHandle("get_node_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
		getOctreeData = lib.findHandle("get_octree_data", FunctionDescriptor.of(OCTREE_DATA, ValueLayout.ADDRESS));
		updateOctree = lib.findHandle("update_octree",
			FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
		);
		importOctree = lib.findHandle("import_octree", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
		getNodeWithLevel = lib.findHandle("get_node_with_level", FunctionDescriptor.of(
			ValueLayout.JAVA_LONG,

			ValueLayout.ADDRESS,
			ValueLayout.JAVA_INT,
			ValueLayout.JAVA_INT,
			ValueLayout.JAVA_INT
		));
		setNode = lib.findHandle("set_node", FunctionDescriptor.of(
			ValueLayout.JAVA_BOOLEAN,

			ValueLayout.ADDRESS,
			ValueLayout.JAVA_INT,
			ValueLayout.JAVA_INT,
			ValueLayout.JAVA_INT,
			ValueLayout.JAVA_INT
		));
	}

	// pub export fn version() [*c]const u8
	private final MethodHandle version;

	public String version() {
		try {
			return ((MemorySegment) version.invokeExact()).getUtf8String(0);
		} catch(Throwable e) {
			throw new ZigError(e);
		}
	}

	// pub export fn get_error() [*c]const u8
	private final MethodHandle getError;

	public String getError() {
		try {
			return ((MemorySegment) getError.invokeExact()).getUtf8String(0);
		} catch(Throwable e) {
			throw new ZigError(e);
		}
	}

	// pub export fn create_octree(depth: c_int) ?*octree.PackedOctree
	private final MethodHandle createOctree;

	public MemorySegment createOctree(int depth) {
		MemorySegment result;
		try {
			result = (MemorySegment) createOctree.invokeExact(depth);
		} catch(Throwable e) {
			throw new ZigError(e);
		}
		if(result.address() == 0)
			throw new ZigError(getError());
		return result;
	}

	// pub export fn destroy_octree(octree: *octree.PackedOctree) void
	private final MethodHandle destroyOctree;

	public void destroyOctree(MemorySegment octree) {
		try {
			destroyOctree.invoke(octree);
		} catch(Throwable e) {
			throw new ZigError(e);
		}
	}

	// pub export fn get_node_count(octree: *const PackedOctree) c_int
	private final MethodHandle getNodeCount;

	public int getNodeCount(MemorySegment octree) {
		try {
			return (int) getNodeCount.invoke(octree);
		} catch(Throwable e) {
			throw new ZigError(e);
		}
	}

	// pub export fn get_octree_data(octree: *PackedOctree) OctreeData
	private final MethodHandle getOctreeData;

	public ByteBuffer getOctreeData(Arena arena, MemorySegment octree) {
		try {
			var octreeData = (MemorySegment) getOctreeData.invoke(arena, octree);

			long capacity = octreeData.get(ValueLayout.JAVA_LONG, 0);
			long length = octreeData.get(ValueLayout.JAVA_LONG, 8);
			MemorySegment pointer = octreeData.get(ValueLayout.ADDRESS, 16);
			System.out.println(STR."OctreeData: capacity=\{capacity} length=\{length} pointer=@\{Long.toUnsignedString(pointer.address(), 16)}");

			var treeDataPtr = MemorySegment.ofAddress(pointer.address());
			var treeData = treeDataPtr.reinterpret(capacity * Integer.BYTES);
			var buf = treeData.asByteBuffer();
			return buf.slice(0, (int) (length * Integer.BYTES)).order(ByteOrder.LITTLE_ENDIAN);
		} catch(Throwable e) {
			throw new ZigError(e);
		}
	}

	// pub export fn update_octree(
	//    octree: *octree.PackedOctree,
	//    x: c_int, y: c_int, z: c_int,
	//    data: [*]c_int, length: c_int,
	//) void
	private final MethodHandle updateOctree;

	public void updateOctree(MemorySegment octree, int x, int y, int z, MemorySegment data) {
		boolean success;
		try {
			success = (boolean) updateOctree.invoke(octree, x, y, z, data.address(), (int) (data.byteSize() / Short.BYTES));
		} catch(Throwable e) {
			throw new ZigError(e);
		}
		if(!success)
			throw new ZigError(getError());
	}

	// pub export fn import_octree(
	//    octree: *octree.PackedOctree,
	//    address: [*]const i32,
	//    length: usize
	//) void
	private final MethodHandle importOctree;

	public void importOctree(MemorySegment octree, MemorySegment data) {
		boolean success;
		try {
			success = (boolean) importOctree.invoke(octree, data.address(), (int) (data.byteSize() / Integer.BYTES));
		} catch(Throwable e) {
			throw new ZigError(e);
		}
		if(!success)
			throw new ZigError(getError());
	}

	// pub export fn get_node_with_level(
	//    octree: *PackedOctree,
	//    x: c_uint, y: c_uint, z: c_uint
	//) PackedOctree.NodeDataWithLevel
	private final MethodHandle getNodeWithLevel;

	public int getNode(MemorySegment octree, int x, int y, int z) {
		try {
			var nodeDataWithLevel = (long) getNodeWithLevel.invoke(octree, x, y, z);
			return (int) (nodeDataWithLevel & 0x7FFFFFFFL);
		} catch(Throwable e) {
			throw new ZigError(e);
		}
	}

	public void getNodeWithLevel(MemorySegment octree, IntIntMutablePair nodeDataAndLevel, int x, int y, int z) {
		try {
			var nodeDataWithLevel = (long) getNodeWithLevel.invoke(octree, x, y, z);
			nodeDataAndLevel
				.left((int) (nodeDataWithLevel & 0x7FFFFFFFL))
				.right((int) (nodeDataWithLevel >> 32));
		} catch(Throwable e) {
			throw new ZigError(e);
		}
	}

	// pub export fn set_node(
	//    octree: *PackedOctree,
	//    x: c_uint, y: c_uint, z: c_uint,
	//    data: c_uint
	//) bool
	private final MethodHandle setNode;

	public void setNode(MemorySegment octree, int x, int y, int z, int data) {
		boolean success;
		try {
			success = (boolean) setNode.invoke(octree, x, y, z, data);
		} catch(Throwable e) {
			throw new ZigError(e);
		}
		if(!success)
			throw new ZigError(getError());
	}

	static class ZigError extends RuntimeException {

		public ZigError(String message) {
			super(message);
		}

		public ZigError(Throwable ex) {
			super(ex);
		}
	}
}
