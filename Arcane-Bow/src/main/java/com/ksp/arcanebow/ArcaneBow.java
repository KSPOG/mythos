package com.ksp.arcanebow;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod("arcanebow")
public final class ArcaneBow {
    private static final String TAG="ksp_arcane_bow_model";
    private static final String SHOT="ksp_arcane_bow_arrow";
    private static final String MARKER="ksp_arcane_bow";
    private final Map<UUID,AbstractArrow> arrows=new HashMap<>();
    private int tick;
    public ArcaneBow(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::register);bus.addListener(this::join);bus.addListener(this::tick);
        bus.addListener((ServerStoppingEvent e)->arrows.clear());
    }
    // Dimensions in model units, centered on the grip; XY plane, Z thickness.
    private record Part(String material,double x,double y,double angle,double w,double h,double depth){}
    private static final List<Part> PARTS=new ArrayList<>();
    private static void rod(String block,double ax,double ay,double bx,double by,double thickness,double depth){
        double dx=bx-ax,dy=by-ay;
        PARTS.add(new Part(block,(ax+bx)/2,(ay+by)/2,Math.atan2(dy,dx),Math.hypot(dx,dy)+thickness*.18,thickness,depth));
    }
    private static void gem(double x,double y){
        PARTS.add(new Part("purple_concrete",x,y,Math.PI/4,.22,.22,.17));
        PARTS.add(new Part("diamond_block",x,y,Math.PI/4,.15,.15,.23));
        PARTS.add(new Part("light_blue_concrete",x-.035,y+.035,Math.PI/4,.068,.068,.25));
    }
    static {
        // Mirrored curved limbs; segmented accents and drawn string with nocked arrow.
        double[][] limb={{0,0},{-.16,.26},{-.20,.52},{-.13,.78},{.03,1.01},{.29,1.14},{.42,1.10}};
        for(int side:new int[]{-1,1}){
            for(int i=0;i<limb.length-1;i++){
                double[] a=limb[i],b=limb[i+1];
                rod(i<2?"black_concrete":"purple_concrete",a[0],a[1]*side,b[0],b[1]*side,.13,.16);
                rod(i%2==0?"purple_terracotta":"blackstone",a[0]+.043,a[1]*side,b[0]+.043,b[1]*side,.053,.18);
            }
            gem(-.15,.35*side);
            double ax=.42,ay=1.10*side,bx=.83,by=0;
            String[] palette={"cyan_concrete","light_blue_concrete","diamond_block","white_concrete","diamond_block"};
            for(int i=0;i<10;i++){
                double u=i/10.0,v=(i+1)/10.0;
                rod(palette[i%palette.length],ax+(bx-ax)*u,ay+(by-ay)*u,ax+(bx-ax)*v,ay+(by-ay)*v,.038,.045);
            }
        }
        rod("black_concrete",0,-.22,0,.22,.15,.20);
        rod("purple_concrete",-.045,-.22,-.045,.22,.043,.22);
        rod("cyan_concrete",.83,0,-.78,0,.055,.055);
        rod("diamond_block",.35,0,.08,0,.07,.07);
        rod("light_blue_concrete",-.78,0,-.61,.075,.06,.075);
        rod("light_blue_concrete",-.78,0,-.61,-.075,.06,.075);
        rod("white_concrete",-.78,0,-.88,0,.07,.07);
    }
    private static String num(double x){return String.format(Locale.ROOT,"%.6ff",x);}
    private static String vec(double... v){StringJoiner j=new StringJoiner(",","[","]");for(double x:v)j.add(num(x));return j.toString();}
    private static String transform(Part p,double scale){
        double c=Math.cos(p.angle),s=Math.sin(p.angle);
        // Rotate a scaled cube about its center, not its vanilla lower corner.
        double x=(p.x-(c*p.w-s*p.h)/2)*scale;
        double y=(p.y-(s*p.w+c*p.h)/2)*scale;
        return "{translation:"+vec(x,y,-p.depth*scale/2)+",left_rotation:"+vec(0,0,Math.sin(p.angle/2),Math.cos(p.angle/2))+",scale:"+vec(p.w*scale,p.h*scale,p.depth*scale)+",right_rotation:"+vec(0,0,0,1)+"}";
    }
    private static int run(CommandSourceStack s,String cmd)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        return s.getServer().getCommands().getDispatcher().execute(cmd,s.withSuppressedOutput());
    }
    private int spawn(CommandSourceStack s,float scale)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        s.getPlayerOrException();
        Vec3 look=s.getPlayerOrException().getLookAngle();
        Vec3 origin=s.getPosition().add(look.x*3,1.8,look.z*3);
        String group="ksp_ab_"+UUID.randomUUID().toString().replace("-","");
        int count=0;
        try{
            var at=s.withPosition(origin);
            for(Part p:PARTS){
                int n=run(at,"summon minecraft:block_display ~ ~ ~ {Tags:[\""+TAG+"\",\""+group+"\"],block_state:{Name:\"minecraft:"+p.material+"\"},billboard:\"vertical\",brightness:{block:15,sky:15},view_range:1.0f,transformation:"+transform(p,scale)+"}");
                if(n!=1)throw new IllegalStateException("Display spawn failed");
                count++;
            }
        }catch(Exception ex){
            run(s.withPosition(origin),"kill @e[type=minecraft:block_display,tag="+group+",distance=..8]");
            s.sendFailure(Component.literal("Arcane Bow model creation failed; partial model cleaned up."));
            return 0;
        }
        int created=count;
        s.sendSuccess(()->Component.literal("Arcane Bow model spawned: "+created+" parts. /arcanebow clear removes nearby models."),false);return count;
    }
    private int give(CommandSourceStack s)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        s.getPlayerOrException();
        // Vanilla bow retains normal draw, ammo, durability and projectile mechanics.
        return run(s,"give @s minecraft:bow[minecraft:custom_data={"+MARKER+":1b},minecraft:custom_name='{\"text\":\"Arcane Bow\",\"color\":\"dark_purple\",\"italic\":false}',minecraft:enchantment_glint_override=true] 1");
    }
    private void join(EntityJoinLevelEvent e){
        if(!(e.getLevel() instanceof ServerLevel)||!(e.getEntity() instanceof AbstractArrow a))return;
        if(a.getTags().contains(SHOT)){arrows.put(a.getUUID(),a);return;}
        // Only attribute newly spawned arrows to the weapon used by a server player.
        if(e.loadedFromDisk()||!(a.getOwner() instanceof ServerPlayer p))return;
        var weapon=a.getWeaponItem();
        if(weapon==null||weapon.isEmpty()||!weapon.is(net.minecraft.world.item.Items.BOW))return;
        var data=weapon.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if(data==null||!data.copyTag().getBoolean(MARKER))return;
        a.addTag(SHOT);a.setBaseDamage(a.getBaseDamage()+2.0);arrows.put(a.getUUID(),a);
        ((ServerLevel)e.getLevel()).sendParticles(ParticleTypes.ENCHANT,a.getX(),a.getY(),a.getZ(),12,.15,.15,.15,.05);
    }
    private void tick(TickEvent.ServerTickEvent.Post e){
        tick++;
        var it=arrows.values().iterator();
        while(it.hasNext()){
            var a=it.next();
            if(a.isRemoved()||a.onGround()||a.getDeltaMovement().lengthSqr()<.0001){it.remove();continue;}
            if(tick%2==0&&a.level() instanceof ServerLevel level){
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,a.getX(),a.getY(),a.getZ(),2,.03,.03,.03,0);
                level.sendParticles(ParticleTypes.END_ROD,a.getX(),a.getY(),a.getZ(),1,0,0,0,0);
            }
        }
    }
    private void register(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("arcanebow").requires(s->s.hasPermission(2))
            .then(Commands.literal("spawn").executes(c->spawn(c.getSource(),1f))
                .then(Commands.argument("scale",FloatArgumentType.floatArg(.25f,3f)).executes(c->spawn(c.getSource(),FloatArgumentType.getFloat(c,"scale")))))
            .then(Commands.literal("give").executes(c->give(c.getSource())))
            .then(Commands.literal("clear").executes(c->{
                var s=c.getSource();return run(s,"kill @e[type=minecraft:block_display,tag="+TAG+",distance=..32]");
            })));
    }
}
