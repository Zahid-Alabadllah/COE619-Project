import java.util.*;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicyMinimumMigrationTime;
import org.cloudsimplus.allocationpolicies.migration.*;
public class MigrationMapRegression {
 public static void main(String[] args) throws Exception {
  var policy=new VmAllocationPolicyMigrationStaticThreshold(new VmSelectionPolicyMinimumMigrationTime(),.8);
  var method=VmAllocationPolicyMigrationAbstract.class.getDeclaredMethod("getMigrationMapFromOverloadedHosts",Set.class);
  method.setAccessible(true);
  @SuppressWarnings("unchecked") Map<Vm,Host> map=(Map<Vm,Host>)method.invoke(policy,Set.of());
  boolean mutable=true;
  try { map.put(Vm.NULL,Host.NULL); } catch(UnsupportedOperationException e) { mutable=false; }
  if(mutable!=Boolean.parseBoolean(args[0])) throw new AssertionError("Unexpected empty-map mutability: "+mutable);
  System.out.println("PASS: empty migration map mutable="+mutable);
 }
}