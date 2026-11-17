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
  new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9,writer) {
   @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions) {
    MethodVisitor next=super.visitMethod(access,name,desc,sig,exceptions);
    if(!name.equals("getMigrationMapFromOverloadedHosts")) return next;
    return new MethodVisitor(Opcodes.ASM9,next) {
     @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf) {
      if(opcode==Opcodes.INVOKESTATIC && owner.equals("java/util/Collections") && method.equals("emptyMap") && descriptor.equals("()Ljava/util/Map;")) {
       super.visitTypeInsn(Opcodes.NEW,"java/util/HashMap");
       super.visitInsn(Opcodes.DUP);
       super.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/HashMap","<init>","()V",false);
       changes[0]++;
      } else super.visitMethodInsn(opcode,owner,method,descriptor,itf);
     }
    };
   }
  },0);
  if(changes[0]!=1) throw new IllegalStateException("Expected exactly one known defective return path, found "+changes[0]);
  Path output=Path.of(args[1]).resolve(entry);
  Files.createDirectories(output.getParent());Files.write(output,writer.toByteArray());
  System.out.println("Applied one mutable-map repair to generated runtime classes; original dependency jar unchanged.");
 }
}