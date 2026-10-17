# Client compatibility checks

Run `python tests/compat/run.py --apk /path/to/client.apk --java-home /path/to/jdk`.

The runner compiles pure Java safety tests, checks reflection overload selection and cache isolation, extracts production call sites and exact hook parameter lists, and resolves them against actual APK DEX. It also validates native settings row factories and direct/callback alternatives, with negative controls that remove the required routes.

`features.md` groups the verdict by source/feature. A new missing required site fails the run. Play-only branch exemptions are scoped to `org.telegram.messenger`; the legacy UI and non-Play secret viewer are explicitly listed. Known dynamic selectors are reviewed separately; new selectors require updating the review list. Framework hooks and injected module DEX cannot be proved by host APK inspection.

Limitations: source extraction is conservative and is not a Java compiler or a full control-flow analysis. Reflection argument *values*, field contents, dynamically created listener instances, networking and media behavior still need device tests. Nine dynamic selector groups are explicitly documented. The Play branch's conditional parameter lists are checked; other clients require their own device and branch validation. No APK compatibility result authorizes a release automatically.

Reports: `call-sites.tsv`, `feature-verdict.tsv`, `features.md`, `dynamic-sites.txt`, `package.txt`, `mapping.txt`, `symbols.tsv`.

The separate Android instrumentation suite exercises the production reflection helpers and actual platform dialog button dispatch, including failed callback logging without crashing the UI.

Telegram 13 adaptation also validates Runnable-returning secret schedulers, the typed close callback, sponsored deserializer/empty constructors and the object-returning cached-ad getter against the real DEX. Negative controls replace required media/ad mappings and must fail. This supplements the call-site inventory for the reflective selector paths.

The runtime empty-ad factory supports constructor-stripped responses, with JVM and Android failure/collection-initialization regressions. The Play APK DEX gate still requires the known empty response constructors present in the verified target builds; this fallback does not silently relax publication checks or certify other clients.

The inventory also records two-argument HMethod registrations with reflected Method objects. Their media/ad shapes and settings routes are checked by the existing DEX audits, while live menu listener routing needs device interaction. An unreviewed new selector or a removed reviewed selector fails the gate; parser/gate regressions cover both cases.

Privacy protocol checks validate all eleven intercepted request decoders against their actual bytecode decode calls (Bool, affectedMessages or Vector), the four local-response constructors, eight read/status/error fields and both ordinary/timestamp delegate interfaces. Negative controls deliberately remove the Bool response, replace the story Vector with a Bool constructor, remove the offline field and replace the timestamp interface with the ordinary interface. These checks supplement the hook inventory: a successful dispatcher registration alone does not establish that its local response still matches the host protocol. They do not prove server-side read visibility or lifecycle behavior.
