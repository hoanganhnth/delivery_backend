import fs from 'node:fs';
import path from 'node:path';

function bootArtifact(backend, group) {
  const pom = path.join(backend, group, 'boot/pom.xml');
  if (!fs.existsSync(pom)) return null;
  const source = fs.readFileSync(pom, 'utf8')
    .replace(/<!--[\s\S]*?-->/g, '').replace(/<parent>[\s\S]*?<\/parent>/g, '');
  const artifact = source.match(/<artifactId>\s*([^<]+?)\s*<\/artifactId>/)?.[1];
  if (!artifact) throw new Error(`Missing boot artifactId: ${pom}`);
  return artifact;
}

export function serviceForSource(backend, file) {
  const group = path.relative(backend, file).split(path.sep)[0];
  return bootArtifact(backend, group) ?? group;
}

export function serviceSourceRoots(backend) {
  const roots = [];
  for (const entry of fs.readdirSync(backend, { withFileTypes: true })) {
    if (!entry.isDirectory() || entry.name.startsWith('.') || entry.name === 'docs') continue;
    const artifact = bootArtifact(backend, entry.name);
    const directories = artifact
      ? ['boot/src/main/java', 'infrastructure/src/main/java'] : ['src/main/java'];
    for (const suffix of directories) {
      const directory = path.join(backend, entry.name, suffix);
      if (fs.existsSync(directory)) roots.push({ service: artifact ?? entry.name, directory });
    }
  }
  return roots;
}
