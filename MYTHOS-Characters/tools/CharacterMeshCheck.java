package com.ksp.mythoscharacters;
public final class CharacterMeshCheck {
    public static void main(String[] args){
        for(boolean slim:new boolean[]{false,true}){
            int previous=Integer.MAX_VALUE;
            for(int lod=0;lod<4;lod++){
                var parts=CharacterMesh.build(slim,lod);
                if(parts.size()!=12)throw new AssertionError("Missing body or overlay");
                int count=0;
                for(var part:parts)for(var q:part.quads()){
                    count+=2;
                    for(var v:new CharacterMesh.Vertex[]{q.a(),q.b(),q.c(),q.d()}){
                        var n=v.normal();var p=v.position();
                        if(!Float.isFinite(p.x()+p.y()+p.z()+n.x()+n.y()+n.z())||v.u()<0||v.u()>1||v.v()<0||v.v()>1)throw new AssertionError("Invalid vertex");
                        if(Math.abs(n.x()*n.x()+n.y()*n.y()+n.z()*n.z()-1)>.001)throw new AssertionError("Invalid normal");
                    }
                }
                if(lod==0&&count<80000)throw new AssertionError("Cinematic mesh undersampled");
                if(count>=previous||count>150000)throw new AssertionError("LOD budget");
                previous=count;System.out.println("slim="+slim+" LOD="+lod+" triangles="+count);
            }
        }
    }
}
