package com.npcarchitects;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class SchematicImport {
    static final int MAX_VOLUME=32768;
    record Plan(int width,int height,int depth,List<Blueprint.Cell> cells) {}
    private SchematicImport() {}

    static BlockState parseState(String value) {
        try {
            if(!value.startsWith("minecraft:")) throw new IllegalArgumentException("Only vanilla block states supported: "+value);
            var state=BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),value,false).blockState();
            if(state.hasBlockEntity()||!state.getFluidState().isEmpty()) throw new IllegalArgumentException("Block entities and fluids are not supported: "+value);
            return state;
        } catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            throw new IllegalArgumentException("Unknown or invalid block state: "+value,ex);
        }
    }

    static Plan read(Path path,int degrees) throws IOException {
        if(Files.size(path)>8L*1024*1024) throw new IOException("Compressed file exceeds 8 MiB");
        CompoundTag root=NbtIo.readCompressed(path,NbtAccounter.create(16L*1024*1024));
        CompoundTag data=root.contains("Schematic",Tag.TAG_COMPOUND)?root.getCompound("Schematic"):root;
        int version=data.getInt("Version");
        if(version!=2&&version!=3) throw new IOException("Only Sponge schematic versions 2 and 3 are supported");
        int w=data.getShort("Width")&65535,h=data.getShort("Height")&65535,d=data.getShort("Length")&65535;
        if(w<1||h<1||d<1||w>64||h>64||d>64||(long)w*h*d>MAX_VOLUME) throw new IOException("Maximum 64 per axis and "+MAX_VOLUME+" total volume");
        var blocks=version==3?data.getCompound("Blocks"):data;
        if(!blocks.contains("Palette",Tag.TAG_COMPOUND)||!blocks.contains(version==3?"Data":"BlockData",Tag.TAG_BYTE_ARRAY)) throw new IOException("Missing block palette or data");
        if(!blocks.getList("BlockEntities",Tag.TAG_COMPOUND).isEmpty()||!data.getList("Entities",Tag.TAG_COMPOUND).isEmpty()) throw new IOException("Schematics containing block entities or entities are not supported");
        var palette=blocks.getCompound("Palette");
        if(palette.isEmpty()||palette.size()>4096) throw new IOException("Invalid palette size");
        Rotation rotation=switch(degrees) {case 0->Rotation.NONE;case 90->Rotation.CLOCKWISE_90;case 180->Rotation.CLOCKWISE_180;case 270->Rotation.COUNTERCLOCKWISE_90;default->throw new IOException("Invalid rotation");};
        Map<Integer,String> states=new HashMap<>();
        Set<Integer> air=new HashSet<>();
        for(String key:palette.getAllKeys()) {
            if(!palette.contains(key,Tag.TAG_INT)) throw new IOException("Invalid palette entry");
            int id=palette.getInt(key);
            if(id<0||states.containsKey(id)) throw new IOException("Duplicate or negative palette ID");
            var state=parseState(key).rotate(rotation);
            states.put(id,BlockStateParser.serialize(state));
            if(state.isAir()) air.add(id);
        }
        int[] ids=SchematicLayout.decode(blocks.getByteArray(version==3?"Data":"BlockData"),w*h*d);
        var cells=new ArrayList<Blueprint.Cell>();
        for(int i=0;i<ids.length;i++) {
            int id=ids[i];
            if(!states.containsKey(id)) throw new IOException("Unknown palette ID "+id);
            if(air.contains(id)) continue;
            int x=i%w,z=(i/w)%d,y=i/(w*d);
            int[] rotated=SchematicLayout.rotate(x,z,w,d,degrees);
            cells.add(new Blueprint.Cell(rotated[0],y,rotated[1],states.get(id)));
        }
        if(cells.isEmpty()) throw new IOException("Schematic contains no building blocks");
        return new Plan(degrees%180==0?w:d,h,degrees%180==0?d:w,List.copyOf(cells));
    }
}
