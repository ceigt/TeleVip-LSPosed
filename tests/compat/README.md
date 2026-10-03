# Client compatibility checks

Run `python tests/compat/run.py --apk /path/to/client.apk --java-home /path/to/jdk`.

The runner compiles pure Java safety tests, checks reflection overload selection and cache isolation, extracts production call sites and exact hook parameter lists, and resolves them against actual APK DEX. It also validates native settings row factories and direct/callback alternatives, with negative controls that remove the required routes.

`features.md` groups the verdict by source/feature. A new missing required site fails the run. Play-only branch exemptions are scoped to `org.telegram.messenger`; the legacy UI and non-Play secret viewer are explicitly listed. Known dynamic selectors are reviewed separately; new selectors require updating the review list. Framework hooks and injected module DEX cannot be proved by host APK inspection.

Limitations: source extraction is conservative and is not a Java compiler or a full control-flow analysis. Reflection argument *values*, field contents, dynamically created listener instances, networking and media behavior still need device tests. Four dynamic selectors are explicitly documented. The Play branch's conditional parameter lists are checked; other clients require their own device and branch validation. No APK compatibility result authorizes a release automatically.

Reports: `call-sites.tsv`, `feature-verdict.tsv`, `features.md`, `dynamic-sites.txt`, `package.txt`, `mapping.txt`, `symbols.tsv`.

The separate Android instrumentation suite exercises the production reflection helpers and actual platform dialog button dispatch, including failed callback logging without crashing the UI.
