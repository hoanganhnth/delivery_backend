#!/usr/bin/env python3
"""Package freshness and owned Compose fixture construction for crash proofs."""
import json
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

LABEL = 'delivery.saga-match-crash.owner'
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
LAYOUTS = {'saga-orchestrator-service': 'dispatch/boot', 'match-service': 'match/boot'}
MATCH_DEFAULTS = {
    'MATCH_OUTBOX_RELAY_ENABLED': 'false',
    'MATCH_CANCELLATION_PROJECTION_RELAY_ENABLED': 'true',
    'MATCH_REDIS_HOST': 'redis',
    'MATCH_REDIS_TIMEOUT': '1000ms',
    'MATCH_KAFKA_FIND_LISTENER_AUTO_STARTUP': 'false',
    'MATCH_KAFKA_STOP_LISTENER_AUTO_STARTUP': 'true',
}


def select_jar(service, layout=None):
    layout = LAYOUTS.get(service, layout)
    if not layout or not Path(layout, 'pom.xml').is_file():
        raise ValueError(f'Missing current source layout for {service}: {layout}')
    document = ET.parse(Path(layout, 'pom.xml')).getroot()
    artifact = document.findtext('m:artifactId', namespaces=NS)
    version = document.findtext('m:version', namespaces=NS) or document.findtext('m:parent/m:version', namespaces=NS)
    final_name = document.findtext('m:build/m:finalName', namespaces=NS)
    if artifact != service or not version or '${' in version or (final_name and '${' in final_name):
        raise ValueError(f'Cannot select an exact package from {layout}/pom.xml')
    jar = Path(layout, 'target', (final_name or f'{artifact}-{version}') + '.jar').resolve()
    if not jar.is_file():
        raise ValueError(f'Missing {jar}; clean-package current services first.')
    projects = {}
    for pom in Path('.').rglob('pom.xml'):
        if any(part in {'target', '.git', 'docs'} for part in pom.parts):
            continue
        artifact = ET.parse(pom).getroot().findtext('m:artifactId', namespaces=NS)
        if artifact:
            projects.setdefault(artifact, []).append(pom.resolve())
    inputs = {Path('pom.xml').resolve()}
    visited = set()

    def visit(pom):
        if pom in visited:
            return
        visited.add(pom)
        inputs.add(pom)
        inputs.update(source for source in (pom.parent / 'src/main').rglob('*') if source.is_file())
        document = ET.parse(pom).getroot()
        parent = document.find('m:parent', NS)
        if parent is not None:
            relative = parent.findtext('m:relativePath', default='../pom.xml', namespaces=NS)
            parent_pom = (pom.parent / relative).resolve() if relative else None
            if parent_pom and parent_pom.is_file():
                visit(parent_pom)
        dependencies = document.findall('m:dependencies/m:dependency', NS)
        dependencies += [dependency for dependency in document.findall(
            'm:dependencyManagement/m:dependencies/m:dependency', NS)
            if dependency.findtext('m:scope', namespaces=NS) == 'import']
        for dependency in dependencies:
            if dependency.findtext('m:scope', default='compile', namespaces=NS) in {'test', 'provided'}:
                continue
            if dependency.findtext('m:groupId', namespaces=NS) != 'com.delivery':
                continue
            artifact = dependency.findtext('m:artifactId', namespaces=NS)
            candidates = projects.get(artifact, [])
            if len(candidates) != 1:
                raise ValueError(f'Missing or ambiguous local runtime dependency source {artifact}')
            visit(candidates[0])

    visit(Path(layout, 'pom.xml').resolve())
    stale = sorted(str(source) for source in inputs if source.stat().st_mtime_ns > jar.stat().st_mtime_ns)
    if stale:
        raise ValueError(f'Stale {service} JAR; clean-package current sources first: ' + ', '.join(stale[:5]))
    return jar


def owned_config(config, owner):
    config.pop('name', None)
    config.pop('include', None)
    services = config['services']
    services = {name: service for name, service in services.items() if not service.get('profiles')}
    config['services'] = services
    for kind in ('networks', 'volumes'):
        for name, resource in config.get(kind, {}).items():
            resource.pop('external', None)
            resource['name'] = f'{owner}-{name}'
            resource['labels'] = {LABEL: owner}
    for name, service in services.items():
        service.pop('container_name', None)
        service.pop('ports', None)
        service['labels'] = {LABEL: owner}
        if name == 'api-gateway':
            service['ports'] = [{'target': 8079, 'published': '0', 'host_ip': '127.0.0.1', 'protocol': 'tcp'}]
        if 'build' in service:
            layout = service['build']['args']['SERVICE_PATH']
            jar = select_jar(name, layout).relative_to(Path.cwd())
            service['image'] = f'{owner}-{name}:fixture'
            service['build'] = {
                'context': str(Path.cwd()),
                'labels': {LABEL: owner},
                'dockerfile_inline': '\n'.join([
                    'FROM amazoncorretto:17-alpine',
                    'RUN apk add --no-cache wget && addgroup -S -g 10001 delivery '
                    '&& adduser -S -D -H -u 10001 -G delivery delivery',
                    'WORKDIR /app',
                    'COPY --chown=delivery:delivery ' + json.dumps([str(jar), 'app.jar']),
                    'USER 10001:10001',
                    'HEALTHCHECK --interval=15s --timeout=3s --start-period=120s --retries=12 '
                    'CMD wget -q -T 3 -O /dev/null "http://localhost:${MANAGEMENT_SERVER_PORT:-9090}/actuator/health/readiness" || exit 1',
                    'ENTRYPOINT ["java", "-jar", "app.jar"]',
                ]),
            }
    services['saga-orchestrator-service']['environment'].update({
        'MATCHING_INITIAL_MAX_RETRY_ATTEMPTS': '2', 'MATCHING_INITIAL_DELAY_SECONDS': '1',
        'MATCHING_INITIAL_MAX_DELAY_SECONDS': '2', 'MATCHING_INITIAL_BACKOFF_MULTIPLIER': '1.0',
    })
    return config


def main():
    if sys.argv[1] == 'jars':
        for service in LAYOUTS:
            print(select_jar(service))
    elif sys.argv[1] == 'fixture':
        print(json.dumps(owned_config(json.load(sys.stdin), sys.argv[2])))
    elif sys.argv[1] == 'match':
        path = Path(sys.argv[2])
        config = json.loads(path.read_text())
        environment = config['services']['match-service']['environment']
        environment.update({key: os.environ.get(key, value) for key, value in MATCH_DEFAULTS.items()})
        environment['SPRING_DATA_REDIS_HOST'] = environment['MATCH_REDIS_HOST']
        path.write_text(json.dumps(config))
    else:
        raise ValueError('Unsupported fixture operation')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, ET.ParseError) as error:
        sys.exit(str(error))
