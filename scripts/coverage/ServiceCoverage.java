import org.jacoco.core.analysis.*; import org.jacoco.core.tools.ExecFileLoader; import java.io.*; import java.nio.file.*; import java.util.*;
public class ServiceCoverage { public static void main(String[] a) throws Exception {
 Path root=Paths.get(a[0]); for (int i=1;i<a.length;i++){ String s=a[i]; ExecFileLoader l=new ExecFileLoader(); CoverageBuilder cb=new CoverageBuilder();
  List<Path> mods=new ArrayList<>(); try(var st=Files.list(root.resolve(s))){st.filter(p->Files.isDirectory(p.resolve("target"))).forEach(mods::add);}
  for(Path m:mods){Path e=m.resolve("target/jacoco.exec"); if(Files.exists(e)) l.load(e.toFile());}
  Analyzer an=new Analyzer(l.getExecutionDataStore(),cb);
  for(Path m:mods){if(m.getFileName().toString().equals("boot"))continue; Path c=m.resolve("target/classes"); if(Files.exists(c)) an.analyzeAll(c.toFile());}
  int lc=0,lt=0,bc=0,bt=0; for(IClassCoverage c:cb.getClasses()){lc+=c.getLineCounter().getCoveredCount();lt+=c.getLineCounter().getTotalCount();bc+=c.getBranchCounter().getCoveredCount();bt+=c.getBranchCounter().getTotalCount();}
  System.out.printf("%-12s lines %5.1f%% (%d/%d)  branches %5.1f%%%n",s,lt==0?0:100.0*lc/lt,lc,lt,bt==0?0:100.0*bc/bt);}}}
