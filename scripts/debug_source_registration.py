"""Narrow default-test registration proof for Debug source boundary admission.
Not a Gradle/Kotlin interpreter: unsupported registration/discovery shapes fail closed.
Canonical source task identity and a retained actual verification log remain independent requirements.
"""
import re
import hashlib
import os
import tomllib
import zipfile
from pathlib import Path
from functools import lru_cache


@lru_cache(maxsize=64)
def runtime_sdk_mods(build):
    """Resolve literal default runtimeOnly Curse SDK coordinates to actual cached mod IDs."""
    cache=Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))/'caches/modules-2/files-2.1'
    result=set()
    for coordinate in re.findall(r'runtimeOnly(?:\s*\(|\s+)\s*(?:fg\.deobf\s*\(?\s*)?[\"\'](curse\.maven:[^:\"\'\s]+:[^\"\'\s]+)',build):
        group,artifact,version=coordinate.split(':')[:3]
        for jar in (cache/group/artifact/version).glob('*/*.jar'):
            if jar.name.endswith(('-sources.jar','-javadoc.jar')): continue
            if jar.is_symlink(): raise ValueError('symlink runtime SDK is not registration proof')
            with zipfile.ZipFile(jar) as archive:
                if 'META-INF/mods.toml' in archive.namelist():
                    metadata=tomllib.loads(archive.read('META-INF/mods.toml').decode())
                    result.update(row['modId'] for row in metadata.get('mods',[]) if isinstance(row.get('modId'),str) and '$' not in row['modId'])
    return result


def guards_admit(body, call_position, sdk_mods):
    import ast
    for gate in re.finditer(r'\bif\s*\(',body[:call_position]):
        condition,end=block(body,body.index('(',gate.start()),'(',')')
        following=end+1
        while following<len(body) and body[following].isspace(): following+=1
        if following>=len(body): continue
        if body[following]=='{':
            _,close=block(body,following)
            if not following<call_position<close: continue
        elif following!=call_position:
            continue
        condition=re.sub(r'ModList\.get\(\)\.isLoaded\("([a-z0-9_.-]+)"\)',lambda m:str(m[1] in sdk_mods),condition)
        condition=condition.replace('&&',' and ').replace('||',' or ')
        condition=re.sub(r'!(?!=)',' not ',condition).strip()
        tree=ast.parse(condition,mode='eval')
        def boolean(node):
            if isinstance(node,ast.Expression): return boolean(node.body)
            if isinstance(node,ast.Constant) and isinstance(node.value,bool): return node.value
            if isinstance(node,ast.UnaryOp) and isinstance(node.op,ast.Not): return not boolean(node.operand)
            if isinstance(node,ast.BoolOp) and isinstance(node.op,(ast.And,ast.Or)):
                values=[boolean(v) for v in node.values]
                return all(values) if isinstance(node.op,ast.And) else any(values)
            raise ValueError('unproven conditional native test registration')
        if not boolean(tree): raise ValueError('native test registration guard is false for default literal runtime SDK mods')
    return True


def block(text, opening, left='{', right='}'):
    depth, quote, escape = 0, None, False
    for index in range(opening, len(text)):
        char = text[index]
        if quote:
            if escape: escape = False
            elif char == '\\': escape = True
            elif char == quote: quote = None
            continue
        if char in ('"', "'"): quote = char; continue
        if char == left: depth += 1
        if char == right:
            depth -= 1
            if depth == 0: return text[opening + 1:index], index
    raise ValueError('unterminated registration block')


def without_comments(text):
    # Preserve literals and newlines; annotation-like comments are never proof.
    tokens = r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
    return re.sub(tokens, lambda m: '\n' * m[0].count('\n') if m[0].startswith(('//','/*')) else m[0], text)


def real_annotation(segment, source, qualified):
    short=qualified.rsplit('.',1)[1]
    alias='|kotlin\\.test\\.Test' if qualified=='org.junit.jupiter.api.Test' else ''
    match=re.search(r'@(?:'+re.escape(qualified)+alias+'|'+short+r')\b(?:\s*\(([^)]*)\))?',segment)
    imported=re.search(r'(?m)^import\s+'+re.escape(qualified.rsplit('.',1)[0])+r'\.(?:'+short+r'|\*)\s*;?\s*$',source)
    if qualified=='org.junit.jupiter.api.Test': imported=imported or re.search(r'(?m)^import\s+kotlin\.test\.(?:Test|\*)\s*;?\s*$',source)
    if match and match[0].startswith('@'+short) and not imported:
        raise ValueError('short annotation does not resolve to actual '+qualified)
    return match


def declaration(source, method, generator=None):
    source = without_comments(source)
    name = re.escape(method)
    masked = re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', lambda m: ''.join('\n' if c=='\n' else ' ' for c in m[0]), source)
    returns=r'(?:void|fun)' if generator is None else r'Collection\s*<\s*TestFunction\s*>'
    matches = list(re.finditer(r'\b'+returns+r'\s+(?:' + name + '|`' + name + r'`)\s*\(', masked))
    if len(matches) != 1: raise ValueError('required exact test declaration absent/ambiguous')
    start = matches[0].start()
    previous = max(masked.rfind('}',0,start),masked.rfind('{',0,start),masked.rfind(';',0,start))
    annotations = source[previous+1:start]
    class_prefix = source[:re.search(r'\b(?:class|object)\s+\w+',masked).start()] if re.search(r'\b(?:class|object)\s+\w+',masked) else ''
    if re.search(r'@(?:[\w.]+\.)?(Disabled\w*|Enabled\w*|Ignore|TestFactory|TestTemplate)\b', annotations + class_prefix):
        raise ValueError('disabled/conditional/generated test is not default exact execution proof')
    _,parameters_end=block(masked,masked.index('(',matches[0].start()),'(',')')
    opening=masked.find('{',parameters_end+1)
    if opening<0 or re.search(r'[;=]',masked[parameters_end+1:opening]):
        raise ValueError('test method has no supported concrete block body')
    method_body,_=block(masked,opening)
    if re.search(r'\b(?:assumeTrue|assumeFalse|assumingThat|TestAbortedException)\b',method_body):
        raise ValueError('conditional/aborted test body is not default execution proof')
    native = real_annotation(annotations,source,'net.minecraft.gametest.framework.GameTest')
    junit = real_annotation(annotations,source,'org.junit.jupiter.api.Test')
    generated=real_annotation(annotations,source,'net.minecraft.gametest.framework.GameTestGenerator')
    if sum(map(bool,(native,junit,generated)))!=1: raise ValueError('required method lacks one real test annotation')
    if generated:
        actual_body,_=block(source,opening)
        if not generator or hashlib.sha256(actual_body.encode()).hexdigest()!=generator.get('body_sha256') or not generator.get('required_function_ids') or len(set(generator['required_function_ids']))!=len(generator['required_function_ids']):
            raise ValueError('unreviewed/changed finite required GameTestGenerator contract')
    if native and re.search(r'\brequired\s*=\s*false\b',native[1] or ''):
        raise ValueError('optional GameTest is not required verification')
    return source, ('forge-generator' if generated else 'forge-gametest' if native else 'junit'), class_prefix


def explicit_registration(repo, class_name, build):
    call = re.compile(r'\b\w+\.register\(\s*(?:[\w.]+\.)?'+re.escape(class_name)+r'(?:\.class|::class\.java)\s*\)')
    for candidate in [*(repo/'src/main').glob('**/*.java'),*(repo/'src/main').glob('**/*.kt')]:
        if candidate.is_symlink(): raise ValueError('symlink native registrar')
        text=without_comments(candidate.read_text())
        if not call.search(text): continue
        for method in re.finditer(r'\b(?:void|fun)\s+(\w+)\s*\([^)]*RegisterGameTestsEvent[^)]*\)',text):
            opening=text.find('{',method.end())
            if opening<0: continue
            body,_=block(text,opening)
            invocation=call.search(body)
            if not invocation: continue
            if re.search(r'\bwhen\s*\(|\belse\b',body): raise ValueError('unsupported alternate native registration branch')
            try: guards_admit(body,invocation.start(),runtime_sdk_mods(build))
            except (SyntaxError,ValueError) as error: raise ValueError('native registration fixture conditions unproven: '+str(error))
            callback=re.search(r'addListener\(\s*(?:this|\w+)?::'+re.escape(method[1])+r'\s*\)',text)
            subscribed='@SubscribeEvent' in text[max(0,method.start()-200):method.start()] and '@Mod.EventBusSubscriber' in text and re.search(r'bus\s*=\s*(?:[\w.]+\.)?Bus.MOD\b',text)
            if (callback and 'getModEventBus' in text and re.search(r'@Mod\s*\(',text)) or subscribed:
                return dict(path=str(candidate.relative_to(repo)),method=method[1],default_runtime_sdk_mod_ids=sorted(runtime_sdk_mods(build)),registration='explicit RegisterGameTestsEvent class wired on MOD bus; enclosing ModList guards resolved against actual mod IDs of literal pinned default runtimeOnly SDK archives')
    raise ValueError('native class lacks holder or a default unconditional wired RegisterGameTestsEvent registrar')


def inspect(repo, test, mod_id, verification_log):
    path = repo / test['path']
    source, kind, prefix = declaration(path.read_text(),test['method'],test.get('generator'))
    if kind=='forge-generator':
        enum=repo/test['generator']['enum_path']
        if '..' in enum.parts or enum.is_symlink() or hashlib.sha256(enum.read_bytes()).hexdigest()!=test['generator']['enum_sha256']:
            raise ValueError('finite native generator case enum changed')
    build_files = [p for p in (repo/'build.gradle.kts',repo/'build.gradle') if p.is_file()]
    if len(build_files)!=1: raise ValueError('unproven Gradle build-file selection')
    build_file=build_files[0]
    build = without_comments(build_file.read_text())
    properties = {}
    props = repo/'gradle.properties'
    if props.is_file():
        properties = dict(re.findall(r'(?m)^\s*([A-Za-z_][\w.]*)\s*=\s*([^\n]+)',props.read_text()))
    variables = {**properties, **dict(re.findall(r'\bval\s+(\w+)(?:\s*:\s*String)?\s*=\s*"([^"\n]*)"',build))}
    variables.setdefault('modId',properties.get('mod_id',mod_id))
    def resolve(expression):
        expression = expression.strip()
        if re.fullmatch(r'property\("mod_id"\)\s+as\s+String',expression):
            return properties.get('mod_id','')
        if expression in variables: return variables[expression]
        if expression in properties: return properties[expression]
        parts = re.split(r'\s+\+\s+',expression)
        values=[]
        for part in parts:
            if part.startswith(('"',"'")) and part.endswith(part[0]):
                value=part[1:-1]
                for key,item in variables.items(): value=value.replace('${'+key+'}',item).replace('$'+key,item)
                if '$' in value: raise ValueError('unresolved native namespace interpolation')
                values.append(value)
            elif re.fullmatch(r'(?:[\w.]+\.)?[A-Z][A-Z0-9_]*',part):
                owner,_,constant=part.rpartition('.')
                if not owner:
                    imported=re.search(r'(?m)^import\s+(?:static\s+)?([\w.]+)\.'+re.escape(constant)+r'\s*;?$',source)
                    if not imported: raise ValueError('unresolved native holder constant')
                    owner=imported[1]
                owner=owner.split('.')[-1]
                candidates=list((repo/'src').glob('**/'+owner+'.java'))+list((repo/'src').glob('**/'+owner+'.kt'))
                found=[]
                for candidate in candidates:
                    found += re.findall(r'\b'+re.escape(constant)+r'(?:\s*:\s*String)?\s*=\s*"([^"\n]+)"',without_comments(candidate.read_text()))
                if len(set(found)) != 1: raise ValueError('unresolved/ambiguous native holder constant')
                values.append(found[0])
            else: raise ValueError('unsupported native namespace expression: '+part)
        return ''.join(values)
    area=path.relative_to(repo/'src').parts[0]
    if kind=='junit':
        if re.search(r'@kotlin\.test\.Test|import\s+kotlin\.test\.(?:Test|\*)',source) and 'test-junit5' not in build:
            # Kotlin JVM 1.9.22's standard kotlin("test") framework inference selects
            # kotlin-test-junit5 for the configured JUnit Platform test task. Require
            # this exact supported plugin + explicit Jupiter, no competing adapter or
            # resolution override; unknown/custom dependency selection stays blocked.
            mapped = (re.search(r'kotlin\("jvm"\)\s+version\s+"1\.9\.22"',build)
                and 'testImplementation(kotlin("test"))' in build and 'org.junit.jupiter:junit-jupiter:' in build
                and 'useJUnitPlatform()' in build and not re.search(r'test-junit["\)]|useJUnit\(|resolutionStrategy|dependencySubstitution',build))
            if not mapped: raise ValueError('Kotlin Test alias lacks proven JUnit5 actual adapter selection')
        if area!='test' or 'useJUnitPlatform()' not in build or re.search(r'includeTestsMatching|excludeTestsMatching|setIncludes|setExcludes',build):
            raise ValueError('unproven default unfiltered JUnit source set')
        if not re.search(r'(?m)^> Task :test(?:\s|$)',verification_log) or re.search(r'(?m)^> Task :test (?:SKIPPED|NO-SOURCE|UP-TO-DATE)',verification_log):
            raise ValueError('canonical verification log lacks actual JUnit execution')
        return dict(kind=kind,annotation='@Test',source_set=area,execution_task='test',default_selection='unfiltered JUnit Platform',build_file=build_file.name)
    holder=real_annotation(prefix,source,'net.minecraftforge.gametest.GameTestHolder')
    registrar=None if holder else explicit_registration(repo,path.stem,build)
    namespace=resolve(holder[1]) if holder else mod_id
    if not re.fullmatch(r'[a-z0-9_.-]+',namespace): raise ValueError('invalid native test holder namespace')
    source_pattern=(r'source\(\s*sourceSets(?:\.main(?:\.get\(\))?|\["main"\])\s*\)' if area=='main'
                    else r'source\(\s*'+re.escape(area)+r'\s*\)')
    if not re.search(source_pattern,build) and not re.search(r'\bsource\s+sourceSets\.'+re.escape(area)+r'\b',build) and not re.search(r'source\(\s*sourceSets\["'+re.escape(area)+r'"\]\s*\)',build):
        # Recognize an explicitly named sourceSet alias, never assume extra test sources compile.
        alias=re.search(r'val\s+(\w+)\s*=\s*sourceSets\.create\("'+re.escape(area)+r'"\)',build)
        if not alias or not re.search(r'source\(\s*'+alias[1]+r'\s*\)',build):
            raise ValueError('native test source set is not wired into the fixture')
    execution=[]
    for prop in re.finditer(r'property(?:\(\s*"forge.enabledGameTestNamespaces"\s*,|\s+\'forge.enabledGameTestNamespaces\'\s*,)',build):
        if build[prop.start():prop.end()].startswith('property('):
            arguments,end=block(build,build.index('(',prop.start()),'(',')')
            expression=arguments.split(',',1)[1]
        else:
            expression=build[prop.end():].split('\n',1)[0].split(';',1)[0].strip()
        try: enabled=resolve(expression).split(',')
        except ValueError: continue
        if namespace not in enabled: continue
        contexts=[]
        for config in re.finditer(r'create\("([A-Za-z0-9]+)"\)\s*\{',build[:prop.start()]):
            opening=build.index('{',config.start())
            _,closing=block(build,opening)
            if closing>=prop.start() and config[1].endswith('GameTestServer'):
                contexts.append(config[1])
        name=contexts[-1] if contexts else 'gameTestServer'
        task='run'+name[0].upper()+name[1:]
        if re.search(r'(?m)^> Task :'+task+r'(?:\s|$)',verification_log) and not re.search(r'(?m)^> Task :'+task+r' (?:SKIPPED|NO-SOURCE|UP-TO-DATE)',verification_log):
            execution.append(task)
    required_counts=[int(value) for value in re.findall(r'All ([1-9][0-9]*) required tests passed',verification_log)]
    minimum=test.get('generator',{}).get('required_namespace_minimum',1)
    if not execution or not required_counts or max(required_counts)<minimum:
        raise ValueError('holder namespace/default native fixture did not actually execute and pass required tests')
    return dict(kind=kind,annotation='@GameTestGenerator' if kind=='forge-generator' else '@GameTest',generator=test.get('generator'),holder_expression=holder[1] if holder else None,explicit_registrar=registrar,namespace=namespace,
                source_set=area,execution_tasks=sorted(set(execution)),default_selection='all registered required functions in enabled holder namespace',build_file=build_file.name)
