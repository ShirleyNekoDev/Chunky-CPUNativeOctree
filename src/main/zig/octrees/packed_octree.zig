const std = @import("std");

const MAX_SIZE_TYPE = u28;
const MAX_SIZE = std.math.maxInt(MAX_SIZE_TYPE);
const OctreeSize = MAX_SIZE_TYPE;
const NodeId = MAX_SIZE_TYPE;
pub const OctreeData = MAX_SIZE_TYPE;

pub const OctreeNode = packed struct(u32) {
    // xxxxyyyy yyyyyyyy yyyyyyyy yyyyyyyy
    dataUnion: DataUnion, // y bits
    type: Type,           // x bits
    const This = @This();

    const INVALID: OctreeNode = .{
        .type = Type.invalid,
        .dataUnion = .{}
    };
    inline fn isInvalid(this: This) bool {
        return this.type == Type.invalid;
    }
    inline fn absolutePointerNode(address: NodeId) OctreeNode {
        return OctreeNode {
            .type = Type.ptr_abs,
            .dataUnion = DataUnion { 
                .ptr_abs = U28AbsolutePointer { .address = address }
            }
        };
    }
    inline fn isAbsolutePointer(this: This) bool {
        return this.type == Type.ptr_abs;
    }
    inline fn asAbsolutePointer(this: This) U28AbsolutePointer {
        std.debug.assert(this.isAbsolutePointer());
        return this.dataUnion.ptr_abs;
    }
    inline fn relativePointerNode(offset: i28) OctreeNode {
        return OctreeNode {
            .type = Type.ptr_rel,
            .dataUnion = DataUnion { 
                .ptr_rel = U28RelativePointer { .offset = offset }
            }
        };
    }
    inline fn isRelativePointer(this: This) bool {
        return this.type == Type.ptr_rel;
    }
    inline fn asRelativePointer(this: This) U28RelativePointer {
        std.debug.assert(this.isRelativePointer());
        return this.dataUnion.ptr_rel;
    }
    inline fn dataNode(data: OctreeData) OctreeNode {
        return OctreeNode {
            .type = Type.data,
            .dataUnion = DataUnion { 
                .data = U28Data { .data = data } 
            }
        };
    }
    inline fn isData(this: This) bool {
        return this.type == Type.data;
    }
    inline fn asData(this: This) U28Data {
        std.debug.assert(this.isData());
        return this.dataUnion.data;
    }

    inline fn reprI32(this: This) i32 {
        // const x: i28 = @bitCast(this.dataUnion);
        // return @as(i32, x);
        return @bitCast(this);
    }

    fn print(this: This, writer: anytype) void {
        const x: u28 = @bitCast(this.dataUnion);
        writer.print("{s}={x}", .{@tagName(this.type), x});
        // const x: u32 = @bitCast(this);
        // writer.print("[{b:0>32}]", .{x});
    }

    const Type = enum(u4) {
        invalid = 0b0000,
        ptr_abs = 0b0010,
        ptr_rel = 0b0011,
        data    = 0b1000,
    };
    const U28AbsolutePointer = packed struct {
        address: NodeId,
    };
    const U28RelativePointer = packed struct {
        offset: i28,
    };
    const U28Data = packed struct {
        data: OctreeData,
    };
    const DataUnion = packed union {
        invalid: void,
        ptr_abs: U28AbsolutePointer,
        ptr_rel: U28RelativePointer,
        data:    U28Data,
    };
};

pub const PackedOctree = struct {
    allocator: std.mem.Allocator,
    treeData: []OctreeNode,
    freeHead: ?NodeId = null,
    nodeCount: OctreeSize = 0,
    treeSize: OctreeSize = 0,
    depth: Level,

    const Level = u5;
    const DEFAULT_SIZE: usize = 512;
    const NODE_GROWTH_COUNT: u32 = 64*1024;

    const ROOT_NODE: NodeId = 0;
    const INITIAL_ROOT = OctreeNode.dataNode(0);
    pub const REPLACEMENT_VALUE: OctreeData = std.math.maxInt(OctreeData);

    const This = @This();

    // depth > 22 may result in size overflows because the value will be out of bounds of usize=u64 
    pub fn init(depth: Level, allocator: std.mem.Allocator) !*This {
        const octree = try allocator.create(PackedOctree);
        octree.allocator = allocator;
        octree.treeData = try allocator.alloc(OctreeNode, DEFAULT_SIZE);
        octree.freeHead = null;
        octree.treeData[0] = INITIAL_ROOT;
        octree.nodeCount = 1;
        octree.treeSize = 1;
        octree.depth = depth;

        return octree;
    }
    pub fn deinit(this: *This) void {
        this.allocator.free(this.treeData);
        this.allocator.destroy(this);
    }

    const OctOffset = packed struct {
        x: u1, y: u1, z: u1,

        inline fn atLevel(x: u32, y: u32, z: u32, level: Level) OctOffset {
            return @This() {
                .x = @intCast(x>>level & 1),
                .y = @intCast(y>>level & 1),
                .z = @intCast(z>>level & 1),
            };
        }
        inline fn offset(this: @This()) u3 {
            const dx: u3 = @intCast(this.x);
            const dy: u3 = @intCast(this.y);
            const dz: u3 = @intCast(this.z);
            return (dx<<2) | (dy<<1) | dz;
        }
    };

    pub const NodeDataWithLevel = extern struct {
        nodeData: u32,
        level: u8
    };
    pub fn getNode(this: *This, x: u32, y: u32, z: u32) NodeDataWithLevel {
        // std.debug.print("getNode x={d} y={d} z={d}\n", .{x, y, z});
        var nodeIndex = ROOT_NODE;
        var level: Level = this.depth;
        while(true) {
            // std.debug.print("level={d} index={d} > [", .{level, nodeIndex});
            const node = this.treeData[nodeIndex];
            // node.print(std.debug);
            // std.debug.print("] ", .{});
            if(node.isData()) {
                // defer std.debug.print("\n", .{});
                return NodeDataWithLevel {
                    .nodeData = @intCast(node.asData().data),
                    .level = @intCast(level)
                };
            }

            level -= 1;
            const childAddress = node.asAbsolutePointer().address;
            const childOffset = OctOffset.atLevel(x, y, z, level).offset();
            // std.debug.print(">> childAddress={d} childOffset={d}\n", .{childAddress, childOffset});
            nodeIndex = childAddress + childOffset;
        }
        unreachable; // subtraction underflow
    }
    
    pub fn setNode(this: *This, x: u32, y: u32, z: u32, data: OctreeData) !void {
        // std.debug.print("setNode x={d} y={d} z={d} data={d}\n", .{x,y,z,data});
        const newNode = OctreeNode.dataNode(data);

        var parents: [std.math.maxInt(Level)]NodeId = undefined;
        var nodeIndex: NodeId = 0;
        var parentLevel: Level = this.depth - 1;
        var level: Level = parentLevel;
        var node: OctreeNode = undefined;

        while(true) : (level -= 1) {
            // std.debug.print("sow level={d} nodeIndex=@{d}\n", .{level, nodeIndex});
            parents[level] = nodeIndex;

            node = this.treeData[nodeIndex];
            if(node.isData()) {
                // std.debug.print("isData=", .{});
                // node.print(std.debug);
                // std.debug.print("\n", .{});
                if(node.asData().data == data)
                    return;

                const firstChildIndex = try this.allocateSpace(NODE_GROWTH_COUNT);
                // std.debug.print("split firstChildIndex=@{d} -> ", .{firstChildIndex});
                node = this._splitNode(nodeIndex, firstChildIndex, node);
                // node.print(std.debug);
                // std.debug.print("\n", .{});
                parentLevel = level;
            } 
            // else {
            //     std.debug.print("isBranch=", .{});
            //     node.print(std.debug);
            //     std.debug.print("\n", .{});
            // }

            const childAddress = node.asAbsolutePointer().address;
            const childOffset = OctOffset.atLevel(x, y, z, level).offset();
            // std.debug.print("childAddress={d} childOffset={d}\n", .{childAddress, childOffset});
            nodeIndex = childAddress + childOffset;
            // std.debug.print("eow level={d} nodeIndex=@{d}\n", .{level, nodeIndex});
            if(level <= 0) break;
        }

        // std.debug.print("set: ", .{});
        this.treeData[nodeIndex] = newNode;
        // std.debug.print("nodeIndex={d} ", .{nodeIndex});
        // this.treeData[nodeIndex].print(std.debug);
        // std.debug.print("\n", .{});

        // TODO: cleanup
    }
    
    /// Finds an open space in the tree to put 8 nodes and allocates it.
    /// Checks free list first, otherwise tries to append to the end of the tree,
    /// extending (and possibly reallocating) it if needed.
    /// 
    /// _The returned location must be used to insert nodes or directly be freed again,
    /// otherwise it will become inaccessible._
    fn allocateSpace(this: *This, growthNodeCount: usize) !NodeId {
        // are there freed children, which can be reused?
        if(this.freeHead != null) {
            std.debug.print("reusing freeHead\n", .{});
            // freeHead points to the next freed space (8 nodes)
            const freeAddress = this.freeHead.?;
            // freeHeadNode links to the next free address after that
            const freeHeadNode = this.treeData[freeAddress];
            if(freeHeadNode.isAbsolutePointer()) {
                // set freeHead to next address in chain
                this.freeHead = freeHeadNode.asAbsolutePointer().address;
            } else {
                this.freeHead = null;
            }
            // std.debug.print("freeAddress={d}\n", .{freeAddress});
            return freeAddress;
        }

        // tree is full, we have to grow
        if (this.treeSize + 8 > this.treeData.len) {
            // std.debug.print("old[{*}] size={d} capacity={d}\n", .{this.treeData, this.treeSize, this.treeData.len});
            const newSize = this.treeData.len + growthNodeCount;
            // std.debug.print("growing memory {d} -> {d}\n", .{this.treeData.len, newSize});
            const success = this.allocator.resize(this.treeData, newSize);
            if(!success) {
                // std.debug.print("resize failed, doing memcpy\n", .{});
                var newTreeData = try this.allocator.alloc(OctreeNode, newSize);
                // std.debug.print("memcpy {*} => {*}\n", .{this.treeData, newTreeData});
                @memcpy(newTreeData[0..this.treeData.len], this.treeData);
                this.allocator.free(this.treeData);
                this.treeData = newTreeData;
            } else {
                this.treeData.len = newSize;
                // std.debug.print("resize sucess, size={d}\n", .{this.treeData.len});
            }
            // std.debug.print("new[{*}] size={d} capacity={d}\n", .{this.treeData, this.treeSize, this.treeData.len});
        }

        // append to the end of the tree
        const freeAddress: NodeId = this.treeSize;
        // std.debug.print("freeAddress={d}\n", .{freeAddress});
        this.treeSize += 8;
        return freeAddress;
    }

    /// Frees the 8 nodes starting at nodeIndex.
    /// Prepends the nodeIndex to the free list.
    inline fn freeSpace(this: *This, nodeIndex: NodeId) void {
        @memset(this.treeData[nodeIndex..(nodeIndex+8)], OctreeNode.INVALID);
        this.nodeCount -= 8;
        if(this.freeHead != null) {
            this.treeData[nodeIndex] = OctreeNode.absolutePointerNode(this.freeHead.?);
            this.freeHead = nodeIndex;
        }
    }

    /// Splits a node into a 8 new child nodes and makes the node a branch node.
    /// Requires the node to be a data node.
    /// Returns the data of the new parent (branch) node.
    fn splitNode(this: *This, nodeIndex: NodeId, childIndex: NodeId) OctreeNode {
        return this._splitNode(nodeIndex, childIndex, this.treeData[nodeIndex]);
    }
    inline fn _splitNode(this: *This, nodeIndex: NodeId, childIndex: NodeId, node: OctreeNode) OctreeNode {
        const branchNode = OctreeNode.absolutePointerNode(childIndex);
        this.treeData[nodeIndex] = branchNode;
        std.debug.assert(node.isData());
        @memset(this.treeData[childIndex..(childIndex+8)], node);
        this.nodeCount += 8;
        return branchNode;
    }

    /// Assuming that all child nodes are identical, collapses them into the parent node.
    /// Parent node will now be the value of the first child node while the child nodes are freed.
    fn mergeChildren(this: *This, nodeIndex: NodeId) void {
        const node = this.treeData[nodeIndex];
        const childIndex = node.asAbsolutePointer().address;
        const childNode = this.treeData[childIndex];
        _mergeChildren(
            nodeIndex,
            childIndex,
            childNode
        );
    }
    inline fn _mergeChildren(this: *This, nodeIndex: NodeId, childIndex: NodeId, childNode: OctreeNode) void {
        this.treeData[nodeIndex] = childNode;
        this.freeSpace(childIndex);
    }

    // fn writeNode(this: *This, nodeIndex: NodeId, )

    pub fn loadNodes(this: *This, input: []const i32) !void {
        var it = Iterator { .input = input };
        this.nodeCount = 0;
        try this.loadNode(&it, ROOT_NODE);
    }
    const Iterator = struct {
        input: []const i32,
        index: u32 = ROOT_NODE,

        fn next(this: *@This()) ?i32 {
            if(this.index >= this.input.len)
                return null;
            const value = this.input[this.index];
            this.index += 1;
            return value;
        }
    };
    fn loadNode(this: *This, input: *Iterator, nodeIndex: NodeId) !void {
        const BRANCH_NODE: i32 = -1;
        // std.debug.print("addr={d}\n", .{nodeIndex});

        const data = input.next().?;
        // std.debug.print("data={d}\n", .{data});
        if(data == BRANCH_NODE) {
            var childIndex = try this.allocateSpace(NODE_GROWTH_COUNT);
            // std.debug.print("offset={d}\n", .{childIndex - nodeIndex});
            // std.debug.print("PointerNode[{d}]\n", .{nodeIndex});
            this.treeData[nodeIndex] = OctreeNode.absolutePointerNode(childIndex);
            this.nodeCount += 1;
            inline for([_]OctreeSize{0,1,2,3,4,5,6,7}) |i| {
                try this.loadNode(input, childIndex + i);
            }
        } else {
            // std.debug.print("DataNode[{d}]\n", .{nodeIndex});
            this.treeData[nodeIndex] = OctreeNode.dataNode(@intCast(data));
            this.nodeCount += 1;
        }
    }

    pub fn print(this: *const This, writer: anytype) void {
        writer.print("PackedOctree({*}){{\n  treeData={*}[{d}],\n  freeHead={?d},\n  nodeCount={d},\n  treeSize={d},\n  depth={d}\n}}\n", .{
            this,
            // this.allocator, //\n  allocator={any},
            this.treeData,
            this.treeData.len,
            this.freeHead,
            this.nodeCount,
            this.treeSize,
            this.depth
        });
    }
    pub fn printData(this: *const This, writer: anytype) void {
        writer.print("\t[", .{});
        for(this.treeData, 0..) |node, i| {
            if(i >= this.treeSize) break;
            node.print(writer);
            writer.print(", ", .{});
        }
        writer.print("]\n", .{});
    }
};

test "init/deinit octree" {
    var octree = try PackedOctree.init(2, std.testing.allocator);
    octree.deinit();
}
test "load octree data" {
    var octree = try PackedOctree.init(2, std.testing.allocator);
    std.debug.print("\n", .{});
    defer octree.deinit();
    const input = [_]i32{-1, -1, 1, 2, 3, 4, 5, 6, 7, 8, 102, 103, 104, 105, 106, 107, 108};
    try octree.loadNodes(&input);
    // octree.printData(std.debug);
    // const size: u32 = 4*4*4;
    // var i: u32 = 0;
    // while(i<size) : (i+=1) {
    //     const x: u32 = i>>4 & 0b11;
    //     const y: u32 = i>>2 & 0b11;
    //     const z: u32 = i & 0b11;
    //     std.debug.print("i={d} ", .{i});
    //     std.debug.print("x={d} y={d} z={d}\n", .{x, y, z});
    //     const nwl = octree.getNode(x, y, z);
    //     nwl.node.print(std.debug);
    //     std.debug.print(" level={d}\n", .{nwl.level});
    // }
}
