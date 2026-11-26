package com.ksp.mythoseconomy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LedgerTest {
    @TempDir Path dir;
    @Test void transactionsAndReload() throws Exception {
        var path=dir.resolve("gold.json");var a=UUID.randomUUID();var b=UUID.randomUUID();
        var l=new Ledger(path,ex->fail(ex));
        assertEquals(0,l.balance(a));
        assertTrue(l.mint(a,a,100,"initial").success());
        assertTrue(l.transfer(a,b,35,"payment").success());
        assertFalse(l.transfer(a,b,66,"overdraw").success());
        assertFalse(l.transfer(a,a,1,"self").success());
        assertFalse(l.burn(a,a,-1,"negative").success());
        assertTrue(l.burn(a,b,5,"fee").success());
        var reload=new Ledger(path,ex->fail(ex));
        assertEquals(65,reload.balance(a));assertEquals(30,reload.balance(b));
        assertEquals(3,reload.recent(b,10).get(0).sequence());
    }
    @Test void limitsAndFailure() throws Exception {
        var path=dir.resolve("gold.json");var a=UUID.randomUUID();var b=UUID.randomUUID();
        var l=new Ledger(path,ex->{});
        assertTrue(l.mint(a,a,Ledger.MAX_BALANCE,"cap").success());
        assertTrue(l.mint(a,b,1,"initial").success());
        assertFalse(l.transfer(b,a,1,"overflow").success());
        assertEquals(1,l.balance(b));
        Files.createDirectory(dir.resolve("gold.json.tmp"));
        assertFalse(l.transfer(a,b,1,"write failure").success());
        assertFalse(l.available());
        var reload=new Ledger(path,ex->fail(ex));
        assertEquals(Ledger.MAX_BALANCE,reload.balance(a));assertEquals(1,reload.balance(b));
    }
    @Test void merchantReceiptsSurviveReloadAndRejectConflicts() throws Exception {
        var path=dir.resolve("merchant.json");var p=UUID.randomUUID();var tx=UUID.randomUUID();
        var l=new Ledger(path,ex->fail(ex));
        assertTrue(l.mint(p,p,100,"initial").success());
        assertTrue(l.merchantChange(tx,p,30,false,"shop purchase").success());
        var reload=new Ledger(path,ex->fail(ex));
        assertTrue(reload.merchantChange(tx,p,30,false,"shop purchase").success());
        assertFalse(reload.merchantChange(tx,p,31,false,"shop purchase").success());
        assertEquals(70,reload.balance(p));
        assertNotNull(reload.merchantReceipt(tx));
    }
    @Test void invalidStorageRejected() throws Exception {
        var path=dir.resolve("gold.json");Files.writeString(path,"{\"balances\":null}");
        assertThrows(java.io.IOException.class,()->new Ledger(path,ex->{}));
    }
    @Test void tokensAndAtomicCreaturePurchase()throws Exception{
        var path=dir.resolve("tokens.json");var p=UUID.randomUUID();var shop=UUID.randomUUID();
        var l=new Ledger(path,ex->fail(ex));assertTrue(l.mint(p,p,100,"gold").success());
        assertTrue(l.adjust(p,p,"mount_token",50,true,"reward").success());
        assertTrue(l.configureCreature(shop,"mount:traveller",new Ledger.CreatureOffer("mount_token",30,1,1,false)).success());
        assertFalse(l.configureCreature(shop,"pet:cat",new Ledger.CreatureOffer("mount_token",1,1,1,false)).success());
        assertFalse(l.purchaseCreature(shop,p,"mount:traveller","gold",30).success());
        assertTrue(l.purchaseCreature(shop,p,"mount:traveller","mount_token",30).success());
        var reload=new Ledger(path,ex->fail(ex));assertEquals(100,reload.balance(p));assertEquals(20,reload.balance(p,"mount_token"));
        assertTrue(reload.ownsCreature(p,"mount:traveller"));assertEquals(0,reload.creatureOffers(shop).get("mount:traveller").stock());
        assertFalse(reload.purchaseCreature(shop,p,"mount:traveller","mount_token",30).success());
        assertTrue(reload.mint(p,p,1,"gold again").success());assertTrue(reload.ownsCreature(p,"mount:traveller"));
    }
}
