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
    @Test void invalidStorageRejected() throws Exception {
        var path=dir.resolve("gold.json");Files.writeString(path,"{\"balances\":null}");
        assertThrows(java.io.IOException.class,()->new Ledger(path,ex->{}));
    }
}
