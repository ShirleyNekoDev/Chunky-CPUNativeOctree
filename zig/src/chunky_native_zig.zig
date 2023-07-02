const std = @import("std");
const packed_octree = @import("packed_octree.zig");
const PackedOctree = packed_octree.PackedOctree;

const _version: [*:0]const u8 = "0.1.0";
pub export fn version() [*:0]const u8 {
    return _version;
}


var _error: [*:0]const u8 = undefined;
fn set_error(message: [*:0]const u8) void {
    _error = message;
}

pub export fn get_error() [*:0]const u8 {
    return _error;
}


var gpa = std.heap.GeneralPurposeAllocator(.{
    .verbose_log = true,
}){};
// var heapA = std.heap.HeapAllocator.init();
var la = std.heap.loggingAllocator(
    // heapA.allocator()
    gpa.allocator()
);

const OctreeData = extern struct {
    capacity: u64,
    length: u64,
    pointer: *packed_octree.OctreeNode,
};
pub export fn get_octree_data(octree: *const PackedOctree) OctreeData {
    // std.debug.print("{b:0>64}\n", .{@intFromPtr(octree)});
    octree.printData(std.debug);
    return OctreeData {
        .capacity = octree.treeData.len,
        .length = octree.treeSize,
        .pointer = &octree.treeData[0],
    };
}

pub export fn get_node_count(octree: *const PackedOctree) c_int {
    return @intCast(octree.nodeCount);
}

pub export fn create_octree(depth: c_int) ?*PackedOctree {
    if(depth < 0 or depth > 31) {
        set_error("illegal depth (must be between 0 - 31)");
        return null;
    }
    const octree = PackedOctree.init(
        @intCast(depth),
        la.allocator()
    ) catch |err| {
        set_error(@errorName(err));
        return null;
    };
    std.debug.print("successfully created octree {*}\n", .{ octree });
    return octree;
}

pub export fn destroy_octree(octree: *PackedOctree) void {
    octree.deinit();
}

// filename
pub export fn import_octree(
    octree: *PackedOctree,
    address: [*]const i32,
    length: usize
) bool {
    octree.loadNodes(address[0..length]) catch |err| {
        set_error(@errorName(err));
        return false;
    };
    return true;
//     _ = octree;
//     var i: usize = 0;
//     while(i < @min(20, length)) : (i += 1) {
//         const v: i32 = address[i];
//         std.debug.print("{d} {d}/{x}\n", .{i, v, v});
//     }
}

// filename
pub export fn export_octree(
    octree: *PackedOctree
) void {
    _ = octree;
}

pub export fn update_octree(
    octree: *PackedOctree, 
    x: c_uint, y: c_uint, z: c_uint,
    data: [*]c_uint, length: c_uint,
) bool {
    _ = octree;
    std.debug.print("x={} y={} z={} data={*} length={}\n", .{x, y, z, data, length});
    return false;
}

const ANY_TYPE = 0x7FFF_FFFE;
pub export fn set_node(
    octree: *PackedOctree, 
    x: c_uint, y: c_uint, z: c_uint,
    data: c_uint
) bool {
    const d: packed_octree.OctreeData =
        if(data == ANY_TYPE) PackedOctree.REPLACEMENT_VALUE
        else @intCast(data);
    octree.setNode(
        @intCast(x), @intCast(y), @intCast(z), d
    ) catch |err| {
        set_error(@errorName(err));
        return false;
    };
    return true;
}

pub export fn get_node_with_level(
    octree: *PackedOctree, 
    x: c_uint, 
    y: c_uint, 
    z: c_uint
) c_ulonglong {
    const ndwl = octree.getNode(
        @intCast(x), @intCast(y), @intCast(z)
    );
    const level: c_ulonglong = ndwl.level;
    const nodeData: u32 =
        if(ndwl.nodeData == PackedOctree.REPLACEMENT_VALUE) ANY_TYPE
        else ndwl.nodeData;
    return level<<32 | nodeData;
}
