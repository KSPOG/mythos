package com.ksp.mythosmerchants;

import com.google.gson.*;
import com.ksp.mythoseconomy.EconomyApi;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** All stock changes and transaction intents share one atomic file.
 * Ambiguous player inventory changes require explicit operator reconciliation.
 */
final class GoldShops {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    static final long MAX_PRICE=9_000_000_000_000L;
    static final int MAX_STOCK=1_000_000;
    static final class Offer {
        String item; long buy,sell; int stock,max; boolean unlimited;
        Offer(String item,long buy,long sell,int stock,int max,boolean unlimited){this.item=item;this.buy=buy;this.sell=sell;this.stock=stock;this.max=max;this.unlimited=unlimited;}
    }
    static final class Shop {
        List<Offer> offers=new ArrayList<>(); long restockSeconds,lastRestock;
    }
    static final class Pending {
        String id,player,shop,item,phase; int index,count; long price; boolean sale;
    }
    static final class State {
        Map<String,Shop> shops=new HashMap<>(); Map<String,Pending> pending=new HashMap<>(); Set<String> removed=new HashSet<>();
    }
    private State state=new State();
    private Path file; private boolean locked=true;
    void start(MinecraftServer server){
        file=server.getWorldPath(LevelResource.ROOT).resolve("mythos-shops.json");locked=true;
        try{
            state=Files.exists(file)?JSON.fromJson(Files.readString(file),State.class):new State();
            if(state==null||state.shops==null||state.pending==null)throw new IllegalStateException("Invalid shop store");
            if(state.removed==null)state.removed=new HashSet<>();for(String id:state.removed)UUID.fromString(id);
            for(var entry:state.shops.entrySet()){
                UUID.fromString(entry.getKey());var s=entry.getValue();
                if(s==null||s.offers==null||s.offers.size()>45||s.restockSeconds<0||s.restockSeconds>31536000||s.lastRestock<0)throw new IllegalStateException("Invalid shop");
                var seen=new HashSet<String>();
                for(var o:s.offers)if(!valid(o)||!seen.add(o.item))throw new IllegalStateException("Invalid offer");
            }
            for(var entry:state.pending.entrySet()){
                var p=entry.getValue();
                if(p==null||!entry.getKey().equals(p.id)||!Set.of("PREPARED","INVENTORY").contains(p.phase)||p.count<1||p.count>64||p.price<1||p.price>MAX_PRICE||!state.shops.containsKey(p.shop)||item(p.item)==null)throw new IllegalStateException("Invalid pending trade");
                UUID.fromString(p.id);UUID.fromString(p.player);
                var shop=state.shops.get(p.shop);
                if(p.index<0||p.index>=shop.offers.size()||!shop.offers.get(p.index).item.equals(p.item))throw new IllegalStateException("Invalid pending offer");
            }
            locked=false;
        }catch(Exception ex){LOG.error("Gold shops locked",ex);}
    }
    private boolean save(){
        if(locked)return false;
        try{
            var tmp=file.resolveSibling(file.getFileName()+".tmp");
            var bytes=ByteBuffer.wrap(JSON.toJson(state).getBytes(StandardCharsets.UTF_8));
            try(var c=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){while(bytes.hasRemaining())c.write(bytes);c.force(true);}
            Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);return true;
        }catch(Exception ex){locked=true;LOG.error("Gold shop storage locked",ex);return false;}
    }
    static Item item(String id){
        var key=ResourceLocation.tryParse(id);if(key==null||!BuiltInRegistries.ITEM.containsKey(key))return null;
        var result=BuiltInRegistries.ITEM.get(key);return result==Items.AIR?null:result;
    }
    private static boolean valid(Offer o){return o!=null&&item(o.item)!=null&&o.buy>=0&&o.buy<=MAX_PRICE&&o.sell>=0&&o.sell<=MAX_PRICE&&(o.buy==0||o.sell<=o.buy)&&o.max>=0&&o.max<=MAX_STOCK&&o.stock>=0&&o.stock<=o.max;}
    private Shop shop(Villager v){return state.shops.get(v.getUUID().toString());}
    private boolean pendingShop(String id){return state.pending.values().stream().anyMatch(p->p.shop.equals(id));}
    private boolean pendingPlayer(UUID id){return state.pending.values().stream().anyMatch(p->p.player.equals(id.toString()));}
    boolean isRemoved(UUID id){return state.removed.contains(id.toString());}
    boolean available(){return !locked;}
    String busy(Villager v){return locked?"Shop storage locked":pendingShop(v.getUUID().toString())?"Shop has unresolved transaction":null;}
    static Shop copy(Shop s){
        var n=new Shop();if(s!=null){n.restockSeconds=s.restockSeconds;for(var o:s.offers)n.offers.add(new Offer(o.item,o.buy,o.sell,o.stock,o.max,o.unlimited));}n.lastRestock=System.currentTimeMillis();return n;
    }
    Shop snapshot(Villager v){return copy(shop(v));}
    static boolean validShop(Shop s){return s!=null&&s.offers!=null&&s.offers.size()<=45&&s.restockSeconds>=0&&s.restockSeconds<=31536000&&s.offers.stream().allMatch(GoldShops::valid)&&s.offers.stream().map(o->o.item).distinct().count()==s.offers.size();}
    boolean replace(Villager v,Shop s){if(busy(v)!=null||isRemoved(v.getUUID())||!validShop(s))return false;state.shops.put(v.getUUID().toString(),copy(s));return save();}
    boolean markRemoved(Villager v){if(busy(v)!=null)return false;String id=v.getUUID().toString();state.removed.add(id);state.shops.remove(id);return save();}
    String configure(Villager v,String id,long buy,long sell,int stock,int max,boolean unlimited){
        String problem=busy(v);if(problem!=null)return problem;
        var product=item(id);if(product==null)return "Invalid item";
        String key=BuiltInRegistries.ITEM.getKey(product).toString();
        var o=new Offer(key,buy,sell,stock,max,unlimited);if(!valid(o))return "Invalid prices or stock (sell price cannot exceed enabled buy price)";
        var s=state.shops.computeIfAbsent(v.getUUID().toString(),k->new Shop());
        int index=-1;for(int i=0;i<s.offers.size();i++)if(s.offers.get(i).item.equals(key))index=i;
        if(index<0){if(s.offers.size()>=45)return "Maximum 45 offers";s.offers.add(o);}else s.offers.set(index,o);
        return save()?"Saved "+key:"Save failed; shops locked";
    }
    String restock(Villager v,long seconds,boolean now){
        String problem=busy(v);if(problem!=null)return problem;
        var s=shop(v);if(s==null)return "No Gold offers";
        if(now){for(var o:s.offers)o.stock=o.max;s.lastRestock=System.currentTimeMillis();}
        else{s.restockSeconds=seconds;s.lastRestock=System.currentTimeMillis();}
        return save()?"Restock settings saved":"Save failed";
    }
    String remove(Villager v,String id){
        String problem=busy(v);if(problem!=null)return problem;
        var s=shop(v);var product=item(id);if(s==null||product==null)return "Offer missing";
        if(!s.offers.removeIf(o->o.item.equals(BuiltInRegistries.ITEM.getKey(product).toString())))return "Offer missing";
        return save()?"Removed offer":"Save failed";
    }
    String info(Villager v){
        var s=shop(v);if(s==null)return "No Gold offers configured";
        var b=new StringBuilder("Restock seconds: "+s.restockSeconds);
        for(var o:s.offers)b.append("\n").append(o.item).append(" | buy ").append(o.buy).append(" | sell ").append(o.sell).append(" | stock ").append(o.unlimited?"unlimited":o.stock+"/"+o.max);
        return b.toString();
    }
    String pending(){return state.pending.isEmpty()?"No pending Gold trades":String.join("\n",state.pending.values().stream().map(p->p.id+" player="+p.player+" shop="+p.shop+" item="+p.item+" count="+p.count+" price="+p.price+" sale="+p.sale+" phase="+p.phase).toList());}
    // Reconcile only after inspecting player inventory and the economy receipt.
    String resolve(MinecraftServer server,String id,boolean inventoryApplied){
        if(locked||!EconomyApi.available(server))return "Storage unavailable";
        var p=state.pending.get(id);if(p==null)return "Unknown transaction";
        var tx=UUID.fromString(p.id);var receipt=EconomyApi.merchantReceipt(server,tx);
        if(p.sale){
            if(!inventoryApplied&&receipt!=null)return "Gold already credited; reconcile inventory manually first";
            if(inventoryApplied&&receipt==null&&!EconomyApi.merchantChange(server,tx,UUID.fromString(p.player),p.price,true,reason(p)).success())return "Credit failed";
        }else{
            if(inventoryApplied&&receipt==null&&!EconomyApi.merchantChange(server,tx,UUID.fromString(p.player),p.price,false,reason(p)).success())return "Debit failed";
            if(!inventoryApplied&&receipt!=null){
                UUID refund=UUID.nameUUIDFromBytes(("mythos-refund:"+id).getBytes(StandardCharsets.UTF_8));
                if(!EconomyApi.merchantChange(server,refund,UUID.fromString(p.player),p.price,true,"Merchant refund "+id).success())return "Refund failed";
            }
        }
        finish(p,inventoryApplied);
        return locked?"Save failed":"Reconciled "+id;
    }
    private static String reason(Pending p){return "Merchant "+(p.sale?"sale ":"purchase ")+p.id;}
    private void finish(Pending p,boolean applied){
        var o=state.shops.get(p.shop).offers.get(p.index);
        if(applied&&!o.unlimited)o.stock+=p.sale?p.count:-p.count;
        state.pending.remove(p.id);save();
    }
    private boolean near(ServerPlayer p,Villager v){return !isRemoved(v.getUUID())&&v.getTags().contains("mythos_merchant")&&p.isAlive()&&!p.isSpectator()&&!p.hasDisconnected()&&v.isAlive()&&v.level()==p.level()&&p.distanceToSqr(v)<=16;}
    private void refreshStock(Villager v){
        var s=shop(v);if(locked||s==null||s.restockSeconds==0||pendingShop(v.getUUID().toString()))return;
        long now=System.currentTimeMillis();
        if(now-s.lastRestock>=s.restockSeconds*1000){for(var o:s.offers)o.stock=o.max;s.lastRestock=now;save();}
    }
    void open(ServerPlayer p,Villager v){
        if(!near(p,v)||locked||!EconomyApi.available(p.getServer())){say(p,"Gold shop unavailable");return;}
        refreshStock(v);if(locked)return;
        if(shop(v)==null||shop(v).offers.isEmpty()){say(p,"No Gold offers configured");return;}
        p.openMenu(new SimpleMenuProvider((id,inv,player)->new Menu(id,inv,p,v),v.getDisplayName()));
    }
    private static void say(ServerPlayer p,String message){p.sendSystemMessage(Component.literal(message));}
    private static ItemStack plain(Offer o,int count){return new ItemStack(item(o.item),count);}
    private static int matching(Inventory inv,ItemStack sample){
        int count=0;for(int i=0;i<36;i++){var stack=inv.getItem(i);if(ItemStack.isSameItemSameComponents(stack,sample))count+=stack.getCount();}return count;
    }
    private static int space(Inventory inv,ItemStack sample){
        int count=0;for(int i=0;i<36;i++){var stack=inv.getItem(i);if(stack.isEmpty())count+=sample.getMaxStackSize();else if(ItemStack.isSameItemSameComponents(stack,sample))count+=Math.max(0,stack.getMaxStackSize()-stack.getCount());}return count;
    }
    private static void applyInventory(Inventory inv,ItemStack sample,int count,boolean sale){
        for(int i=0;i<36&&count>0;i++){
            var stack=inv.getItem(i);
            if(sale){if(ItemStack.isSameItemSameComponents(stack,sample)){int n=Math.min(count,stack.getCount());stack.shrink(n);count-=n;}}
            else if(ItemStack.isSameItemSameComponents(stack,sample)){int n=Math.min(count,stack.getMaxStackSize()-stack.getCount());stack.grow(n);count-=n;}
        }
        if(!sale)for(int i=0;i<36&&count>0;i++)if(inv.getItem(i).isEmpty()){int n=Math.min(count,sample.getMaxStackSize());inv.setItem(i,sample.copyWithCount(n));count-=n;}
        if(count!=0)throw new IllegalStateException("Inventory changed during trade");inv.setChanged();
    }
    private void trade(ServerPlayer p,Villager v,int index,boolean sale,int quantity){
        var server=p.getServer();if(locked||!near(p,v)||!EconomyApi.available(server)||pendingPlayer(p.getUUID())||pendingShop(v.getUUID().toString())){say(p,"Trade unavailable or pending reconciliation");return;}
        refreshStock(v);if(locked)return;
        var s=shop(v);if(s==null||index<0||index>=s.offers.size())return;
        var o=s.offers.get(index);long unit=sale?o.sell:o.buy;
        if(unit<=0||quantity<1||quantity>64||unit>MAX_PRICE/quantity){say(p,"Trade disabled or invalid quantity");return;}
        long cost=unit*quantity;
        if(!o.unlimited&&(sale?quantity>o.max-o.stock:quantity>o.stock)){say(p,sale?"Merchant stock full":"Out of stock");return;}
        var sample=plain(o,1);var inv=p.getInventory();
        if(sale?matching(inv,sample)<quantity:space(inv,sample)<quantity){say(p,sale?"Not enough matching unmodified items":"Inventory full");return;}
        long balance=EconomyApi.balance(server,p.getUUID());
        if(sale?balance>MAX_PRICE-cost:balance<cost){say(p,sale?"Gold balance limit":"Insufficient Gold");return;}
        var tx=new Pending();tx.id=UUID.randomUUID().toString();tx.player=p.getUUID().toString();tx.shop=v.getUUID().toString();tx.item=o.item;tx.index=index;tx.count=quantity;tx.price=cost;tx.sale=sale;tx.phase="PREPARED";
        state.pending.put(tx.id,tx);if(!save()){say(p,"Shop storage failed");return;}
        try{
            if(!sale&&!EconomyApi.merchantChange(server,UUID.fromString(tx.id),p.getUUID(),cost,false,reason(tx)).success()){
                finish(tx,false);say(p,"Gold debit rejected");return;
            }
            applyInventory(inv,sample,quantity,sale);
            // Save player data before clearing durable intent. PlayerList.save may fail internally;
            // ambiguous crash/write failures still need operator review, not automatic replay.
            server.getPlayerList().saveAll();
            tx.phase="INVENTORY";if(!save()){say(p,"Trade pending; contact staff");return;}
            if(sale&&!EconomyApi.merchantChange(server,UUID.fromString(tx.id),p.getUUID(),cost,true,reason(tx)).success()){say(p,"Sale pending; contact staff");return;}
            finish(tx,true);say(p,locked?"Trade pending; contact staff":(sale?"Sold ":"Purchased ")+quantity+" for "+cost+" Gold");
        }catch(Exception ex){LOG.error("Pending merchant transaction "+tx.id,ex);say(p,"Trade pending; contact staff");}
    }
    private final class Menu extends ChestMenu {
        private final ServerPlayer player;private final Villager npc;private final SimpleContainer display;private final List<String> ids;private List<String> quotes;
        Menu(int id,Inventory inv,ServerPlayer player,Villager npc){this(id,inv,player,npc,new SimpleContainer(54));}
        private Menu(int id,Inventory inv,ServerPlayer player,Villager npc,SimpleContainer display){
            super(MenuType.GENERIC_9x6,id,inv,display,6);this.player=player;this.npc=npc;this.display=display;
            this.ids=shop(npc).offers.stream().map(o->o.item).toList();render();
        }
        private List<String> quotes(Shop s){return s.offers.stream().map(o->o.item+":"+o.buy+":"+o.sell).toList();}
        private void render(){
            display.clearContent();var s=shop(npc);if(s==null)return;
            quotes=quotes(s);
            for(int i=0;i<Math.min(45,s.offers.size());i++){
                var o=s.offers.get(i);var stack=plain(o,1);
                stack.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("Buy: "+(o.buy==0?"disabled":o.buy+" Gold")),Component.literal("Sell: "+(o.sell==0?"disabled":o.sell+" Gold")),Component.literal("Stock: "+(o.unlimited?"Unlimited":o.stock+"/"+o.max)),Component.literal("Left buy 1 / right sell 1"),Component.literal("Shift-left buy 10 / shift-right sell 10"))));
                display.setItem(i,stack);
            }
            var balance=new ItemStack(Items.GOLD_INGOT);balance.set(DataComponents.CUSTOM_NAME,Component.literal("Gold: "+(EconomyApi.available(player.getServer())?EconomyApi.balance(player.getServer(),player.getUUID()):"Unavailable")));display.setItem(49,balance);
            broadcastChanges();
        }
        @Override public boolean stillValid(Player p){return p==player&&near(player,npc)&&!locked;}
        @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
        @Override public void clicked(int slot,int button,ClickType type,Player p){
            // Block all transfers, including player-side shifts, hotbar swaps, drag and throw.
            if(!stillValid(p)||slot<0||slot>=45||(type!=ClickType.PICKUP&&type!=ClickType.QUICK_MOVE)||(button!=0&&button!=1))return;
            var s=shop(npc);if(s==null||!quotes(s).equals(quotes)||slot>=ids.size()||slot>=s.offers.size()||!ids.get(slot).equals(s.offers.get(slot).item)){player.closeContainer();return;}
            trade(player,npc,slot,button==1,type==ClickType.QUICK_MOVE?10:1);render();
        }
        @Override public void removed(Player p){super.removed(p);}
    }
}
