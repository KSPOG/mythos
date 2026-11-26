package com.ksp.mythosequipment;

import com.ksp.mythosequipment.EquipmentRules.*;
import java.util.*;

/** Authored set-specific palette and silhouette. No dependency on client classes. */
final class ArmorDesign {
    record Profile(int primary,int secondary,int trim,int cloth,int crest,int shoulder,int chest,int leg,float bulk,float length) {}
    // Each row follows the six progression tiers, in catalog order.
    private static final int[][] COLORS={
        {0x89939c,0x647786,0x782a32,0x9a4140,0x42252c,0x8a2428},
        {0x595b60,0x77716a,0x343c48,0x354e80,0x262b32,0x365a83},
        {0x6e5037,0x365d32,0x4a647b,0x35475d,0x294d42,0xc1c7b2},
        {0x68332d,0x4d397b,0x76a9be,0x252c64,0x50346f,0x24203f},
        {0x34303b,0x282d39,0x294d37,0x29232f,0x352536,0x161b27},
        {0xb9a68a,0xd5cbb2,0x8d9fad,0xc9a34c,0xded6bd,0xeee4c8},
        {0x33373b,0x434747,0xb8ae91,0x807b68,0x293e38,0x262b31},
        {0x50444b,0x493052,0x644482,0x29213e,0x50314e,0x211b38},
        {0x76634a,0x79583c,0x397b78,0x414f83,0x29606b,0xc1e1d8}
    };
    private static final int[][] CREST={{0,1,2,3,4,5},{1,0,3,2,5,4},{0,2,1,4,3,5},{1,2,3,4,5,0},{0,1,4,2,5,3},{2,1,0,3,4,5},{0,3,2,1,5,4},{1,0,4,3,2,5},{0,4,2,3,5,1}};
    static Profile of(ClassSet c,Tier t){
        int i=c.ordinal(),j=t.ordinal();
        int primary=COLORS[i][j], trim=j>=3?0xd2ac62:j==2?0xb8c4cc:0x907b61;
        if(c==ClassSet.NECROMANCER)trim=0xd1c3a1;
        if(c==ClassSet.ROGUE)trim=j>=4?0x9f72bd:0x69717d;
        if(c==ClassSet.MAGE||c==ClassSet.HEXSLINGER)trim=0xb8a0e4;
        float bulk=switch(c){case HEAVY_KNIGHT->1.27F;case WARRIOR->1.07F;case CLERIC->1.0F;case ROGUE->.78F;case RANGER->.83F;default->.91F;};
        return new Profile(primary,darken(primary),trim,c.color,CREST[i][j],(j+i*2)%6,(j*5+i)%6,(j+i*3)%6,bulk+j*.035F,(c==ClassSet.MAGE||c==ClassSet.NECROMANCER||c==ClassSet.SUMMONER||c==ClassSet.HEXSLINGER?3.0F:0.8F)+j*.35F);
    }
    private static int darken(int rgb){return ((int)(((rgb>>16)&255)*.52)<<16)|((int)(((rgb>>8)&255)*.52)<<8)|(int)((rgb&255)*.52);}
}
