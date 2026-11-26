package com.ksp.mythoscharacters;
public final class CharacterMeshCheck {
    public static void main(String[] args){
        for(boolean slim:new boolean[]{false,true})for(int physique=0;physique<3;physique++)for(boolean hair:new boolean[]{false,true}){
            int previous=Integer.MAX_VALUE;
            for(int lod=0;lod<4;lod++){
                var parts=CharacterMesh.build(slim,lod,physique,hair);
                if(parts.size()!=12)throw new AssertionError("Missing body or overlay");
                int count=0;
                var torso=parts.get(2);
                var front=torso.quads().stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d())).filter(v->Math.abs(v.position().x())<.08F&&v.position().y()>3&&v.position().y()<6&&v.position().z()<-1.4F).findFirst().orElse(null);
                if(lod<=1&&(front==null||front.normal().z()>-.65F))throw new AssertionError("Torso front normals must point outward");
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
                previous=count;System.out.println("physique="+physique+" hair="+hair+" slim="+slim+" LOD="+lod+" triangles="+count);
            }
        }
    }
}
