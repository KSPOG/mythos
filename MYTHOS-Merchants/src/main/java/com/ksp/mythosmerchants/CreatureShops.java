package com.ksp.mythosmerchants;

import com.ksp.mythoseconomy.EconomyApi;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;

final class CreatureShops {
    static String kind(Villager v){
        if(v.getTags().contains("mythos_merchant_type_stable"))return "mount";
        if(v.getTags().contains("mythos_merchant_type_pet"))return "pet";
        return "";
    }
    static boolean near(ServerPlayer p,Villager v){return p.isAlive()&&!p.isSpectator()&&!p.hasDisconnected()&&v.isAlive()&&v.getTags().contains("mythos_merchant")&&v.level()==p.level()&&p.distanceToSqr(v)<=16;}
    static void open(ServerPlayer p,Villager v){
        if(!near(p,v)||!EconomyApi.available(p.getServer()))return;
        p.openMenu(new SimpleMenuProvider((id,inv,player)->new Menu(id,inv,p,v),v.getDisplayName()));
    }
    private record Choice(String key,String currency,long price){}
    private static final class Menu extends ChestMenu {
        final ServerPlayer player;final Villager npc;final SimpleContainer display;
        final Map<Integer,Choice> choices=new HashMap<>();Choice selected;
        Menu(int id,Inventory inv,ServerPlayer p,Villager npc){this(id,inv,p,npc,new SimpleContainer(54));}
        Menu(int id,Inventory inv,ServerPlayer p,Villager npc,SimpleContainer display){
            super(MenuType.GENERIC_9x6,id,inv,display,6);player=p;this.npc=npc;this.display=display;render();
        }
        void render(){
            display.clearContent();choices.clear();var s=player.getServer();
            if(!EconomyApi.available(s))return;
            var offers=EconomyApi.creatureOffers(s,npc.getUUID());int i=0;
            for(var c:EconomyApi.creatures(s,kind(npc))){
                var o=offers.get(c.key());if(!c.shop()||o==null||i>=45)continue;
                var stack=new ItemStack(kind(npc).equals("mount")?Items.SADDLE:Items.NAME_TAG);
                stack.set(DataComponents.CUSTOM_NAME,Component.literal(c.name()));
                stack.set(DataComponents.LORE,new ItemLore(List.of(Component.literal(c.description()),Component.literal("Price: "+o.price()+" "+o.currency()),Component.literal("Stock: "+(o.unlimited()?"Unlimited":o.stock())),Component.literal(EconomyApi.creatureOwned(s,player.getUUID(),c.key())?"Owned":"Click to select"))));
                choices.put(i,new Choice(c.key(),o.currency(),o.price()));display.setItem(i++,stack);
            }
            var wallet=new ItemStack(Items.GOLD_INGOT);wallet.set(DataComponents.CUSTOM_NAME,Component.literal("Wallet"));
            wallet.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("Gold: "+EconomyApi.balance(s,player.getUUID(),"gold")),Component.literal(kind(npc)+" tokens: "+EconomyApi.balance(s,player.getUUID(),kind(npc)+"_token")))));display.setItem(49,wallet);
            if(selected!=null){
                var yes=new ItemStack(Items.LIME_DYE);yes.set(DataComponents.CUSTOM_NAME,Component.literal("Confirm "+selected.key()+" for "+selected.price()+" "+selected.currency()));display.setItem(51,yes);
                var no=new ItemStack(Items.RED_DYE);no.set(DataComponents.CUSTOM_NAME,Component.literal("Cancel"));display.setItem(53,no);
            }
            broadcastChanges();
        }
        @Override public boolean stillValid(Player p){return p==player&&near(player,npc)&&EconomyApi.available(player.getServer());}
        @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
        @Override public void clicked(int slot,int button,ClickType type,Player p){
            if(!stillValid(p)||button!=0||type!=ClickType.PICKUP)return;
            if(slot==51&&selected!=null){var c=selected;selected=null;if(!c.key().startsWith(kind(npc)+":"))return;var receipt=EconomyApi.purchaseCreature(player.getServer(),npc.getUUID(),player.getUUID(),c.key(),c.currency(),c.price());player.sendSystemMessage(Component.literal(receipt.message()));}
            else if(slot==53)selected=null;
            else if(choices.containsKey(slot))selected=choices.get(slot);
            render();
        }
    }
}
