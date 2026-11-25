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
    // The NBT format and Java array indexing impose representational limits, not a gameplay size cap.
    static int volume(int w,int h,int d) throws IOException {
        long size=(long)w*h*d;
        if(w<=0||h<=0||d<=0||size>Integer.MAX_VALUE-8L) throw new IOException("Invalid dimensions or volume exceeds Java array indexing capacity");
        return (int)size;
    }
    record Plan(int width,int height,int depth,List<Blueprint.Cell> cells) {}
    private SchematicImport() {}

    static BlockState parseState(String value) {
        try {
            if(!value.startsWith("minecraft:")) throw new IllegalArgumentException("Only vanilla block states supported: "+value);
            var state=BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),value,false).blockState();
            if((state.hasBlockEntity()&&!isSign(state))||!state.getFluidState().isEmpty()) throw new IllegalArgumentException("Block entities and fluids are not supported: "+value);
            return state;
        } catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            throw new IllegalArgumentException("Unknown or invalid block state: "+value,ex);
        }
    }

    static boolean isSign(BlockState state){
        return state.getBlock() instanceof net.minecraft.world.level.block.SignBlock;
    }
    private static String plainLine(String raw)throws IOException{
        if(raw.length()>4096)throw new IOException("Sign component too long");
        try{
            var e=com.google.gson.JsonParser.parseString(raw);
            String value;
            if(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString())value=e.getAsString();
            else if(e.isJsonObject()&&e.getAsJsonObject().size()==1&&e.getAsJsonObject().has("text")&&e.getAsJsonObject().get("text").isJsonPrimitive()&&e.getAsJsonObject().get("text").getAsJsonPrimitive().isString())value=e.getAsJsonObject().get("text").getAsString();
            else throw new IOException("Only plain literal sign text is supported");
            return value;
        }catch(com.google.gson.JsonParseException|IllegalStateException ex){throw new IOException("Invalid sign JSON",ex);}
    }
    private static Blueprint.SignSide side(CompoundTag n)throws IOException{
        var lines=n.getList("messages",Tag.TAG_STRING);
        if(lines.size()!=4)throw new IOException("Sign requires four text lines per side");
        var values=new ArrayList<String>();
        for(int i=0;i<4;i++)values.add(plainLine(lines.getString(i)));
        // Filtered text can carry distinct content; reject it rather than silently changing it.
        if(n.contains("filtered_messages"))throw new IOException("Filtered sign variants are not supported");
        return new Blueprint.SignSide(values,n.contains("color",Tag.TAG_STRING)?n.getString("color"):"black",n.getBoolean("has_glowing_text"));
    }
    static Plan read(Path path,int degrees) throws IOException {
        long budget=Long.getLong("npcarchitects.nbtBudgetBytes",Math.max(16L*1024*1024,Runtime.getRuntime().maxMemory()/4));
        if(budget<=0) throw new IOException("Invalid NBT memory budget");
        CompoundTag root=NbtIo.readCompressed(path,NbtAccounter.create(budget));
        CompoundTag data=root.contains("Schematic",Tag.TAG_COMPOUND)?root.getCompound("Schematic"):root;
        int version=data.getInt("Version");
        if(version!=2&&version!=3) throw new IOException("Only Sponge schematic versions 2 and 3 are supported");
        int w=data.getShort("Width")&65535,h=data.getShort("Height")&65535,d=data.getShort("Length")&65535;
        int volume=volume(w,h,d);
        var blocks=version==3?data.getCompound("Blocks"):data;
        if(!blocks.contains("Palette",Tag.TAG_COMPOUND)||!blocks.contains(version==3?"Data":"BlockData",Tag.TAG_BYTE_ARRAY)) throw new IOException("Missing block palette or data");
        if(!data.getList("Entities",Tag.TAG_COMPOUND).isEmpty())throw new IOException("Schematic entities are not supported");
        Map<Integer,Blueprint.SignData> signs=new HashMap<>();
        for(var tag:blocks.getList("BlockEntities",Tag.TAG_COMPOUND)){
            var be=(CompoundTag)tag;
            String type=be.getString("Id");
            if(!type.equals("minecraft:sign")&&!type.equals("minecraft:hanging_sign"))throw new IOException("Unsupported block entity: "+type);
            int[] p=be.getIntArray("Pos");
            if(p.length!=3||p[0]<0||p[0]>=w||p[1]<0||p[1]>=h||p[2]<0||p[2]>=d)throw new IOException("Invalid sign position");
            var payload=version==3?be.getCompound("Data"):be;
            int index=p[0]+w*(p[2]+d*p[1]);
            var sign=new Blueprint.SignData(side(payload.getCompound("front_text")),side(payload.getCompound("back_text")),payload.getBoolean("is_waxed"));
            if(signs.putIfAbsent(index,sign)!=null)throw new IOException("Duplicate sign position");
        }
        var palette=blocks.getCompound("Palette");
        if(palette.isEmpty()) throw new IOException("Invalid palette size");
        Rotation rotation=switch(degrees) {case 0->Rotation.NONE;case 90->Rotation.CLOCKWISE_90;case 180->Rotation.CLOCKWISE_180;case 270->Rotation.COUNTERCLOCKWISE_90;default->throw new IOException("Invalid rotation");};
        Map<Integer,String> states=new HashMap<>();
        Set<Integer> air=new HashSet<>(),signIds=new HashSet<>();
        for(String key:palette.getAllKeys()) {
            if(!palette.contains(key,Tag.TAG_INT)) throw new IOException("Invalid palette entry");
            int id=palette.getInt(key);
            if(id<0||states.containsKey(id)) throw new IOException("Duplicate or negative palette ID");
            var state=parseState(key).rotate(rotation);
            states.put(id,BlockStateParser.serialize(state));
            if(state.isAir()) air.add(id);
            if(isSign(state))signIds.add(id);
        }
        int[] ids=SchematicLayout.decode(blocks.getByteArray(version==3?"Data":"BlockData"),volume);
        var cells=new ArrayList<Blueprint.Cell>();
        for(int i=0;i<ids.length;i++) {
            int id=ids[i];
            if(!states.containsKey(id)) throw new IOException("Unknown palette ID "+id);
            if(air.contains(id)){if(signs.containsKey(i))throw new IOException("Sign data on air");continue;}
            int x=i%w,z=(i/w)%d,y=i/(w*d);
            int[] rotated=SchematicLayout.rotate(x,z,w,d,degrees);
            var sign=signs.remove(i);
            boolean isSign=signIds.contains(id);
            if(isSign!=(sign!=null))throw new IOException("Sign block/data mismatch at "+x+","+y+","+z);
            cells.add(new Blueprint.Cell(rotated[0],y,rotated[1],states.get(id),sign));
        }
        if(!signs.isEmpty())throw new IOException("Unmatched sign data");
        if(cells.isEmpty()) throw new IOException("Schematic contains no building blocks");
        return new Plan(degrees%180==0?w:d,h,degrees%180==0?d:w,List.copyOf(cells));
    }
}
