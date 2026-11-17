import java.nio.file.*;
import java.util.jar.*;
import org.objectweb.asm.*;
/** Narrow runtime compatibility repair for CloudSim Plus 8.0.0; never modifies its jar. */
public class MutableMigrationMapPatch {
 public static void main(String[] args) throws Exception {
  final String entry="org/cloudsimplus/allocationpolicies/migration/VmAllocationPolicyMigrationAbstract.class";
  byte[] original;
  try(JarFile jar=new JarFile(args[0])) { original=jar.getInputStream(jar.getJarEntry(entry)).readAllBytes(); }
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
  int[] changes={0};
  final boolean stable=args.length>2 && Boolean.parseBoolean(args[2]);
  int[] orderedMaps={0};
  int[] orderedHosts={0};
  new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9,writer) {
   @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions) {
    MethodVisitor next=super.visitMethod(access,name,desc,sig,exceptions);
    return new MethodVisitor(Opcodes.ASM9,next) {
     @Override public void visitCode() {
      super.visitCode();
      if(stable && name.equals("getMigrationMapFromOverloadedHosts")) {
       // Host.compareTo sorts by ID. Do not let simulation identity hashes
       // determine which overloaded source is planned first.
       super.visitTypeInsn(Opcodes.NEW,"java/util/TreeSet");
       super.visitInsn(Opcodes.DUP);
       super.visitVarInsn(Opcodes.ALOAD,1);
       super.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/TreeSet","<init>","(Ljava/util/Collection;)V",false);
       super.visitVarInsn(Opcodes.ASTORE,1);
       orderedHosts[0]++;
      }
     }
     @Override public void visitTypeInsn(int opcode,String type) {
      if(stable && opcode==Opcodes.NEW && type.equals("java/util/HashMap")) {
       orderedMaps[0]++;
       super.visitTypeInsn(opcode,"java/util/LinkedHashMap");
      } else super.visitTypeInsn(opcode,type);
     }
     @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf) {
      if(name.equals("getMigrationMapFromOverloadedHosts") && opcode==Opcodes.INVOKESTATIC && owner.equals("java/util/Collections") && method.equals("emptyMap") && descriptor.equals("()Ljava/util/Map;")) {
       final String map=stable?"java/util/LinkedHashMap":"java/util/HashMap";
       super.visitTypeInsn(Opcodes.NEW,map);
       super.visitInsn(Opcodes.DUP);
       super.visitMethodInsn(Opcodes.INVOKESPECIAL,map,"<init>","()V",false);
       changes[0]++;
      } else if(stable && opcode==Opcodes.INVOKESPECIAL && owner.equals("java/util/HashMap") && method.equals("<init>")) {
       super.visitMethodInsn(opcode,"java/util/LinkedHashMap",method,descriptor,itf);
      } else super.visitMethodInsn(opcode,owner,method,descriptor,itf);
     }
    };
   }
  },0);
  if(changes[0]!=1) throw new IllegalStateException("Expected exactly one known defective return path, found "+changes[0]);
  if(stable && (orderedMaps[0]!=4 || orderedHosts[0]!=1)) throw new IllegalStateException("Unexpected planning sites: maps="+orderedMaps[0]+" hosts="+orderedHosts[0]);
  Path output=Path.of(args[1]).resolve(entry);
  Files.createDirectories(output.getParent());Files.write(output,writer.toByteArray());
  System.out.println("Applied one mutable-map repair to generated runtime classes; original dependency jar unchanged.");
  if(stable) System.out.println("Opt-in stable planning: four insertion-ordered maps and ID-sorted overloaded sources.");
 }
}
