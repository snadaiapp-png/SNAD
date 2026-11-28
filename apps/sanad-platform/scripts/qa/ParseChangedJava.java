import com.sun.source.util.JavacTask;
import java.util.Arrays;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Parse-only syntax gate. Does NOT compile or type-check project dependencies. */
public final class ParseChangedJava {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> inputs = files.getJavaFileObjectsFromStrings(Arrays.asList(args));
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "-source", "21"), null, inputs);
            int parsed = 0;
            for (var unit : task.parse()) { parsed++; }
            long errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).count();
            diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .forEach(System.err::println);
            System.out.println("PARSED=" + parsed + " ERRORS=" + errors);
            if (errors != 0 || parsed != args.length) System.exit(1);
        }
    }
}
