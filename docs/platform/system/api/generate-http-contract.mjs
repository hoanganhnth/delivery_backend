#!/usr/bin/env node

/**
 * Produces deterministic machine-readable and human-readable source-derived
 * HTTP contract artifacts for the Delivery backend. It deliberately is not
 * presented as OpenAPI: the codebase has no SpringDoc annotations and this
 * extractor must not invent JSON wire semantics that are absent from Java
 * source. Instead it records every mapped controller operation, its Java
 * signature/bindings, and reachable DTO field declarations with source
 * locations.
 *
 * Usage:
 *   node docs/system/api/generate-http-contract.mjs --write
 *   node docs/system/api/generate-http-contract.mjs --check
 */

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
// This copy lives under backend_delivery/docs/platform/system/api, while
// source code and the three client repositories remain siblings of
// backend_delivery in the workspace root.
const workspace = path.resolve(here, '../../../../..');
const backend = path.join(workspace, 'backend_delivery');
const inventoryPath = path.join(backend, 'docs/http-api-inventory.md');
const outputPath = path.join(here, 'http-contract.json');
const catalogPath = path.join(here, 'http-contract-catalog.md');
const write = process.argv.includes('--write');
const check = process.argv.includes('--check');

if ((write && check) || (!write && !check)) {
  console.error('Usage: node docs/system/api/generate-http-contract.mjs --write|--check');
  process.exit(2);
}

const primitiveOrFrameworkTypes = new Set([
  'String', 'Object', 'Void', 'Boolean', 'Byte', 'Short', 'Integer', 'Long',
  'Float', 'Double', 'Character', 'BigDecimal', 'BigInteger', 'UUID',
  'LocalDate', 'LocalDateTime', 'OffsetDateTime', 'Instant', 'Date', 'Time',
  'List', 'Set', 'Map', 'Collection', 'Iterable', 'Optional', 'Stream',
  'Page', 'Pageable', 'PageRequest', 'ResponseEntity', 'HttpEntity',
  'HttpServletRequest', 'HttpServletResponse', 'MultipartFile', 'Principal',
  'Authentication', 'Jwt', 'AuthenticatedActor', 'Mono', 'Flux', 'Model',
]);

function relative(file) {
  return path.relative(workspace, file).split(path.sep).join('/');
}

function walk(directory, predicate, files = []) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      walk(target, predicate, files);
    } else if (entry.isFile() && predicate(target)) {
      files.push(target);
    }
  }
  return files;
}

function lineAt(source, index) {
  return source.slice(0, index).split(/\r?\n/).length;
}

function normalizeWhitespace(value) {
  return value.replace(/\s+/g, ' ').trim();
}

function unquoteCode(value) {
  return value.trim().replace(/^`|`$/g, '');
}

function splitMarkdownRow(row) {
  const cells = [];
  let current = '';
  let escaped = false;
  for (let index = 1; index < row.length - 1; index += 1) {
    const character = row[index];
    if (escaped) {
      current += character;
      escaped = false;
    } else if (character === '\\') {
      escaped = true;
    } else if (character === '|') {
      cells.push(current.trim());
      current = '';
    } else {
      current += character;
    }
  }
  cells.push(current.trim());
  return cells;
}

function readInventory() {
  const inventory = fs.readFileSync(inventoryPath, 'utf8');
  const expectedMatch = inventory.match(/hiện có \*\*(\d+) method\*\*/);
  if (!expectedMatch) throw new Error('Cannot read expected endpoint count from HTTP inventory.');
  const expectedCount = Number.parseInt(expectedMatch[1], 10);
  const heading = '## Exact method inventory';
  const start = inventory.indexOf(heading);
  if (start < 0) throw new Error(`Missing '${heading}' in HTTP inventory.`);
  const rows = [];
  for (const line of inventory.slice(start).split(/\r?\n/)) {
    if (!line.startsWith('|')) continue;
    const cells = splitMarkdownRow(line);
    if (cells.length !== 5 || cells[0] === 'Service' || /^-+$/.test(cells[0])) continue;
    const [service, controller, verb, routePath, handler] = cells.map(unquoteCode);
    if (!service.endsWith('-service')) continue;
    rows.push({
      service,
      controller,
      verbs: verb.split('|').map((item) => item.trim()).filter(Boolean),
      path: routePath,
      handler,
    });
  }
  if (rows.length !== expectedCount) {
    throw new Error(`HTTP inventory has ${rows.length} rows, expected ${expectedCount}.`);
  }
  return { expectedCount, rows };
}

function stripCommentsAndStrings(source) {
  const characters = [...source];
  let state = 'normal';
  for (let index = 0; index < characters.length; index += 1) {
    const current = characters[index];
    const next = characters[index + 1];
    if (state === 'normal') {
      if (current === '/' && next === '/') {
        characters[index] = ' ';
        characters[index + 1] = ' ';
        index += 1;
        state = 'line-comment';
      } else if (current === '/' && next === '*') {
        characters[index] = ' ';
        characters[index + 1] = ' ';
        index += 1;
        state = 'block-comment';
      } else if (current === '"') {
        characters[index] = ' ';
        state = 'string';
      } else if (current === "'") {
        characters[index] = ' ';
        state = 'character';
      }
      continue;
    }
    if (state === 'line-comment') {
      if (current === '\n') state = 'normal';
      else characters[index] = ' ';
      continue;
    }
    if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        characters[index] = ' ';
        characters[index + 1] = ' ';
        index += 1;
        state = 'normal';
      } else if (current !== '\n') {
        characters[index] = ' ';
      }
      continue;
    }
    if (state === 'string' || state === 'character') {
      if (current === '\\') {
        characters[index] = ' ';
        if (index + 1 < characters.length && characters[index + 1] !== '\n') characters[index + 1] = ' ';
        index += 1;
      } else if ((state === 'string' && current === '"') || (state === 'character' && current === "'")) {
        characters[index] = ' ';
        state = 'normal';
      } else if (current !== '\n') {
        characters[index] = ' ';
      }
    }
  }
  return characters.join('');
}

function matchingIndex(source, openIndex, open = '(', close = ')') {
  let depth = 0;
  for (let index = openIndex; index < source.length; index += 1) {
    if (source[index] === open) depth += 1;
    if (source[index] === close) {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  throw new Error(`Unbalanced '${open}${close}' near offset ${openIndex}.`);
}

function splitTopLevel(value, separator = ',') {
  const parts = [];
  let current = '';
  let angle = 0;
  let paren = 0;
  let square = 0;
  let brace = 0;
  let quote = null;
  let escaped = false;
  for (const character of value) {
    if (quote) {
      current += character;
      if (escaped) escaped = false;
      else if (character === '\\') escaped = true;
      else if (character === quote) quote = null;
      continue;
    }
    if (character === '"' || character === "'") {
      quote = character;
      current += character;
      continue;
    }
    if (character === '<') angle += 1;
    if (character === '>') angle = Math.max(0, angle - 1);
    if (character === '(') paren += 1;
    if (character === ')') paren = Math.max(0, paren - 1);
    if (character === '[') square += 1;
    if (character === ']') square = Math.max(0, square - 1);
    if (character === '{') brace += 1;
    if (character === '}') brace = Math.max(0, brace - 1);
    if (character === separator && angle === 0 && paren === 0 && square === 0 && brace === 0) {
      if (current.trim()) parts.push(current.trim());
      current = '';
    } else {
      current += character;
    }
  }
  if (current.trim()) parts.push(current.trim());
  return parts;
}

function annotationMetadata(raw) {
  const annotations = [];
  const matcher = /@([A-Za-z_$][\w.$]*)(?:\s*\(([^)]*)\))?/g;
  for (const match of raw.matchAll(matcher)) {
    annotations.push({ name: match[1].split('.').pop(), arguments: normalizeWhitespace(match[2] ?? '') || undefined });
  }
  return annotations;
}

function fieldMetadata(raw, source, offset) {
  const sourceWithoutComments = raw.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/[^\r\n]*/g, ' ');
  const annotations = annotationMetadata(sourceWithoutComments);
  let remainder = sourceWithoutComments.replace(/@[A-Za-z_$][\w.$]*(?:\s*\([^)]*\))?/g, ' ')
    .replace(/\b(?:public|protected|private|static|final|transient|volatile)\b/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  remainder = remainder.replace(/\s*=.*$/s, '').replace(/;\s*$/, '').trim();
  const nameMatch = remainder.match(/([A-Za-z_$][\w$]*)\s*(?:\[\])?$/);
  if (!nameMatch) return null;
  const name = nameMatch[1];
  let type = remainder.slice(0, nameMatch.index).trim();
  if (remainder.endsWith('[]') && !type.endsWith('[]')) type = `${type}[]`;
  if (!type || /\b(class|interface|record|enum)\b/.test(type)) return null;
  const bindingNames = new Set(['RequestBody', 'RequestParam', 'PathVariable', 'RequestHeader', 'RequestPart', 'AuthenticationPrincipal']);
  const constraints = annotations.filter((annotation) => !bindingNames.has(annotation.name));
  const requiredAnnotation = annotations.find((annotation) => ['NotNull', 'NotBlank', 'NotEmpty'].includes(annotation.name));
  const field = {
    name,
    type: normalizeWhitespace(type),
    annotations,
    required: Boolean(requiredAnnotation),
    sourceLine: lineAt(source, offset),
  };
  if (constraints.length > 0) field.constraints = constraints;
  return field;
}

function parameterMetadata(raw, source, offset) {
  const annotations = annotationMetadata(raw);
  const result = fieldMetadata(raw, source, offset);
  if (!result) return { raw: normalizeWhitespace(raw), annotations };
  const annotationNames = new Set(annotations.map((annotation) => annotation.name));
  if (annotationNames.has('RequestBody')) {
    result.binding = 'body';
    result.required = !annotations.some((annotation) => annotation.name === 'RequestBody' && /required\s*=\s*false/.test(annotation.arguments ?? ''));
  }
  else if (annotationNames.has('RequestPart')) result.binding = 'part';
  else if (annotationNames.has('RequestParam')) result.binding = 'query';
  else if (annotationNames.has('PathVariable')) result.binding = 'path';
  else if (annotationNames.has('RequestHeader')) result.binding = 'header';
  else if (annotationNames.has('AuthenticationPrincipal')) result.binding = 'principal';
  else result.binding = 'framework';
  const requestAnnotation = annotations.find((annotation) => ['RequestParam', 'PathVariable', 'RequestHeader', 'RequestPart'].includes(annotation.name));
  if (requestAnnotation?.arguments) {
    const explicitName = requestAnnotation.arguments.match(/(?:name|value)\s*=\s*"([^"]+)"/)
      ?? requestAnnotation.arguments.match(/^"([^"]+)"$/);
    if (explicitName) result.wireName = explicitName[1];
    if (/required\s*=\s*false/.test(requestAnnotation.arguments)) result.required = false;
    const defaultValue = requestAnnotation.arguments.match(/defaultValue\s*=\s*"([^"]*)"/);
    if (defaultValue) {
      result.defaultValue = defaultValue[1];
      result.required = false;
    }
  }
  if (requestAnnotation) {
    result.wireName ??= result.name;
    if (!/required\s*=\s*false/.test(requestAnnotation.arguments ?? '') && !('defaultValue' in result)) {
      result.required = true;
    }
  }
  delete result.sourceLine;
  return result;
}

function extractTypeNames(value) {
  const matches = value.match(/\b[A-Z][A-Za-z0-9_$]*\b/g) ?? [];
  return [...new Set(matches.filter((name) => !primitiveOrFrameworkTypes.has(name)))];
}

function extractPackageAndImports(source) {
  const packageName = source.match(/^\s*package\s+([\w.]+);/m)?.[1] ?? '';
  const imports = new Map();
  const wildcards = [];
  for (const match of source.matchAll(/^\s*import\s+([\w.]+)(\.\*)?;/gm)) {
    if (match[2]) wildcards.push(match[1]);
    else imports.set(match[1].split('.').pop(), match[1]);
  }
  return { packageName, imports, wildcards };
}

function findMethod(controllerSource, sanitized, handler) {
  const matcher = new RegExp(`\\b${handler.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*\\(`, 'g');
  let match;
  while ((match = matcher.exec(sanitized)) !== null) {
    const handlerIndex = match.index;
    const before = sanitized.slice(Math.max(0, handlerIndex - 8000), handlerIndex);
    const relativePublic = before.lastIndexOf('public');
    if (relativePublic < 0) continue;
    const publicIndex = Math.max(0, handlerIndex - 8000) + relativePublic;
    const between = sanitized.slice(publicIndex, handlerIndex);
    if (/[{};]/.test(between)) continue;
    const openParen = sanitized.indexOf('(', handlerIndex);
    const closeParen = matchingIndex(sanitized, openParen);
    const after = sanitized.slice(closeParen, Math.min(sanitized.length, closeParen + 1000));
    const bodyOffset = after.search(/[;{]/);
    if (bodyOffset < 0) continue;
    const signatureEnd = closeParen + bodyOffset + 1;
    const returnType = normalizeWhitespace(controllerSource.slice(publicIndex + 'public'.length, handlerIndex));
    if (!returnType) continue;
    const parameterSource = controllerSource.slice(openParen + 1, closeParen);
    const parameters = [];
    let runningOffset = openParen + 1;
    for (const parameter of splitTopLevel(parameterSource)) {
      const localIndex = controllerSource.indexOf(parameter, runningOffset);
      parameters.push(parameterMetadata(parameter, controllerSource, localIndex));
      runningOffset = localIndex + parameter.length;
    }
    return {
      returnType,
      signature: normalizeWhitespace(controllerSource.slice(publicIndex, signatureEnd).replace(/[;{]$/, '')),
      sourceLine: lineAt(controllerSource, publicIndex),
      parameters,
    };
  }
  throw new Error(`Cannot resolve public handler '${handler}' from controller source.`);
}

function findClassBody(source, declarationIndex) {
  const open = source.indexOf('{', declarationIndex);
  if (open < 0) throw new Error(`Missing class body near ${declarationIndex}.`);
  return { open, close: matchingIndex(source, open, '{', '}') };
}

function declarationMetadata(file) {
  const source = fs.readFileSync(file, 'utf8');
  const stripped = stripCommentsAndStrings(source);
  const { packageName, imports, wildcards } = extractPackageAndImports(source);
  const declaration = stripped.match(/\b(?:public\s+)?(?:abstract\s+)?(?:final\s+)?(record|class|enum|interface)\s+([A-Za-z_$][\w$]*)/);
  if (!declaration) return null;
  const kind = declaration[1];
  const name = declaration[2];
  const declarationIndex = declaration.index;
  return {
    file,
    source,
    stripped,
    packageName,
    imports,
    wildcards,
    kind,
    name,
    fqcn: packageName ? `${packageName}.${name}` : name,
    declarationIndex,
    sourceLine: lineAt(source, declarationIndex),
  };
}

function schemaFields(definition) {
  const { source, stripped, kind, name, declarationIndex } = definition;
  if (kind === 'record') {
    const recordNameIndex = stripped.indexOf(name, declarationIndex);
    const open = stripped.indexOf('(', recordNameIndex + name.length);
    const close = matchingIndex(stripped, open);
    const components = source.slice(open + 1, close);
    let runningOffset = open + 1;
    return splitTopLevel(components)
      .map((component) => {
        const localIndex = source.indexOf(component, runningOffset);
        runningOffset = localIndex + component.length;
        return fieldMetadata(component, source, localIndex);
      })
      .filter(Boolean);
  }
  if (kind === 'enum') return [];
  const { open, close } = findClassBody(stripped, declarationIndex);
  const fields = [];
  let nestedDepth = 0;
  let statementStart = open + 1;
  for (let index = open + 1; index < close; index += 1) {
    const character = stripped[index];
    if (character === '{') {
      nestedDepth += 1;
      if (nestedDepth === 1) statementStart = index + 1;
      continue;
    }
    if (character === '}') {
      nestedDepth = Math.max(0, nestedDepth - 1);
      statementStart = index + 1;
      continue;
    }
    if (character !== ';' || nestedDepth !== 0) continue;
    const statement = source.slice(statementStart, index + 1);
    statementStart = index + 1;
    const statementWithoutAnnotations = statement.replace(/@[A-Za-z_$][\w.$]*(?:\s*\([^)]*\))?/g, ' ');
    if (/\bstatic\b/.test(statement) || statementWithoutAnnotations.includes('(')) continue;
    const field = fieldMetadata(statement, source, Math.max(open + 1, statementStart - statement.length - 1));
    if (field) {
      fields.push(field);
    }
  }
  return fields;
}

function enumValues(definition) {
  if (definition.kind !== 'enum') return undefined;
  const { open, close } = findClassBody(definition.stripped, definition.declarationIndex);
  const beforeSemicolon = definition.source.slice(open + 1, close).split(';', 1)[0];
  const values = splitTopLevel(beforeSemicolon)
    .map((value) => value.replace(/\/\*[\s\S]*?\*\//g, '').trim().match(/^([A-Za-z_$][\w$]*)/)?.[1])
    .filter(Boolean);
  return values.length > 0 ? values : undefined;
}

function directNestedDefinitions(definition) {
  const { open, close } = findClassBody(definition.stripped, definition.declarationIndex);
  const nested = [];
  let depth = 0;
  for (let index = open + 1; index < close; index += 1) {
    const character = definition.stripped[index];
    if (character === '{') {
      depth += 1;
      continue;
    }
    if (character === '}') {
      depth = Math.max(0, depth - 1);
      continue;
    }
    if (depth !== 0) continue;
    const tail = definition.stripped.slice(index, close);
    const match = tail.match(/^(?:(?:public|protected|private|static|final|abstract)\s+)*(record|class|enum|interface)\s+([A-Za-z_$][\w$]*)/);
    if (!match) continue;
    const [matched, kind, name] = match;
    const nestedDefinition = {
      ...definition,
      kind,
      name,
      fqcn: `${definition.fqcn}.${name}`,
      declarationIndex: index + matched.indexOf(kind),
      sourceLine: lineAt(definition.source, index),
      nestedDefinitions: [],
    };
    nestedDefinition.nestedDefinitions = directNestedDefinitions(nestedDefinition);
    nested.push(nestedDefinition);
    index += matched.length - 1;
  }
  return nested;
}

function buildClassIndex() {
  const files = walk(backend, (file) => file.endsWith('.java') && file.includes(`${path.sep}src${path.sep}main${path.sep}java${path.sep}`));
  const byFqcn = new Map();
  const bySimpleName = new Map();
  for (const file of files) {
    const definition = declarationMetadata(file);
    if (!definition) continue;
    byFqcn.set(definition.fqcn, definition);
    const candidates = bySimpleName.get(definition.name) ?? [];
    candidates.push(definition);
    bySimpleName.set(definition.name, candidates);
  }
  for (const definition of byFqcn.values()) {
    definition.nestedDefinitions = directNestedDefinitions(definition);
  }
  return { byFqcn, bySimpleName };
}

function resolveType(name, context, index) {
  if (!name || primitiveOrFrameworkTypes.has(name)) return null;
  if (name.includes('.')) return index.byFqcn.get(name) ?? null;
  const imported = context.imports.get(name);
  if (imported && index.byFqcn.has(imported)) return index.byFqcn.get(imported);
  const nested = context.nestedDefinitions?.find((definition) => definition.name === name);
  if (nested) return nested;
  const local = context.packageName ? `${context.packageName}.${name}` : '';
  if (local && index.byFqcn.has(local)) return index.byFqcn.get(local);
  for (const wildcard of context.wildcards) {
    const candidate = `${wildcard}.${name}`;
    if (index.byFqcn.has(candidate)) return index.byFqcn.get(candidate);
  }
  const candidates = index.bySimpleName.get(name) ?? [];
  return candidates.length === 1 ? candidates[0] : null;
}

function schemaReference(definition) {
  return `#/schemas/${definition.fqcn}`;
}

function buildSchemas(operations, classIndex) {
  const pending = [];
  const seen = new Set();
  const schemas = {};
  const nestedSchema = (definition) => {
    const fields = schemaFields(definition);
    const schema = {
      kind: definition.kind,
      source: { file: relative(definition.file), line: definition.sourceLine },
      fields: fields.map((field) => {
        const output = { ...field };
        delete output.sourceLine;
        return output;
      }),
    };
    const values = enumValues(definition);
    if (values) schema.values = values;
    if (definition.nestedDefinitions?.length) {
      schema.nestedTypes = Object.fromEntries(definition.nestedDefinitions
        .map((nested) => [nested.name, nestedSchema(nested).schema])
        .sort(([left], [right]) => left.localeCompare(right)));
    }
    return { schema, fields };
  };
  const addTypes = (value, context) => {
    for (const typeName of extractTypeNames(value)) {
      const resolved = resolveType(typeName, context, classIndex);
      if (resolved && !seen.has(resolved.fqcn)) pending.push(resolved);
    }
  };
  for (const operation of operations) {
    addTypes(operation.java.returnType, operation._controllerDefinition);
    for (const parameter of operation.java.parameters) addTypes(parameter.type ?? '', operation._controllerDefinition);
  }
  while (pending.length > 0) {
    const definition = pending.shift();
    if (seen.has(definition.fqcn)) continue;
    seen.add(definition.fqcn);
    const { schema, fields } = nestedSchema(definition);
    schemas[definition.fqcn] = schema;
    for (const field of fields) addTypes(field.type, definition);
  }
  return Object.fromEntries(Object.entries(schemas).sort(([left], [right]) => left.localeCompare(right)));
}

function buildContract() {
  const { expectedCount, rows } = readInventory();
  const classIndex = buildClassIndex();
  const controllerByKey = new Map();
  for (const definition of classIndex.byFqcn.values()) {
    if (!definition.file.includes(`${path.sep}controller${path.sep}`)) continue;
    const module = relative(definition.file).split('/')[1];
    controllerByKey.set(`${module}:${definition.name}`, definition);
  }
  const operations = rows.map((row) => {
    const controller = controllerByKey.get(`${row.service}:${row.controller}`);
    if (!controller) throw new Error(`Cannot locate ${row.service}/${row.controller}.java.`);
    const method = findMethod(controller.source, controller.stripped, row.handler);
    const operation = {
      id: `${row.service}:${row.controller}.${row.handler}:${row.verbs.join('+')}:${row.path}`,
      service: row.service,
      controller: row.controller,
      verbs: row.verbs,
      path: row.path,
      handler: row.handler,
      source: { file: relative(controller.file), line: method.sourceLine },
      java: {
        signature: method.signature,
        returnType: method.returnType,
        parameters: method.parameters,
      },
      _controllerDefinition: controller,
    };
    return operation;
  });
  if (operations.length !== expectedCount) throw new Error('Operation count changed while building the contract.');
  const schemas = buildSchemas(operations, classIndex);
  const publicOperations = operations.map((operation) => {
    const output = { ...operation };
    delete output._controllerDefinition;
    return output;
  });
  const perService = {};
  for (const operation of publicOperations) perService[operation.service] = (perService[operation.service] ?? 0) + 1;
  return {
    format: 1,
    title: 'Delivery source-derived HTTP contract manifest',
    generatedFrom: [
      'backend_delivery/docs/http-api-inventory.md',
      'backend_delivery/*/src/main/java/**/controller/*Controller.java',
      'backend_delivery/*/src/main/java/**/dto/** and payload types reachable from controller signatures',
    ],
    scope: 'Every annotated controller mapping, including internal, hidden, dev-only and experimental endpoints. Consult the HTTP inventory for public-edge classification and capability status.',
    schemaStatus: 'Field metadata is extracted from Java declarations and validation annotations. It is a source map, not an inferred OpenAPI/JSON Schema; controller/DTO source remains authoritative for Jackson, inheritance, polymorphism and error behavior.',
    counts: {
      operations: publicOperations.length,
      services: Object.keys(perService).length,
      sourceSchemas: Object.keys(schemas).length,
      byService: Object.fromEntries(Object.entries(perService).sort(([left], [right]) => left.localeCompare(right))),
    },
    operations: publicOperations,
    schemas,
  };
}

const contract = buildContract();
const content = `${JSON.stringify(contract, null, 2)}\n`;

function markdownCell(value) {
  return String(value ?? '—')
    .replace(/\\/g, '\\\\')
    .replace(/\|/g, '\\|')
    .replace(/\r?\n/g, '<br>');
}

function markdownCode(value) {
  return `\`${markdownCell(value).replace(/`/g, '\\`')}\``;
}

function sourceMarkdownLink(source) {
  if (!source?.file || !source?.line) return '—';
  const target = path.join(workspace, source.file);
  const relativeTarget = path.relative(here, target).split(path.sep).join('/');
  return `[${markdownCode(`${source.file}:${source.line}`)}](${relativeTarget})`;
}

function annotationText(annotations = []) {
  if (!annotations.length) return '—';
  return annotations
    .map((annotation) => `@${annotation.name}${annotation.arguments ? `(${annotation.arguments})` : ''}`)
    .join(', ');
}

function markdownTable(headers, rows) {
  return [
    `| ${headers.map(markdownCell).join(' | ')} |`,
    `| ${headers.map(() => '---').join(' | ')} |`,
    ...rows.map((row) => `| ${row.map(markdownCell).join(' | ')} |`),
  ].join('\n');
}

function operationSort(left, right) {
  return left.service.localeCompare(right.service)
    || left.path.localeCompare(right.path)
    || left.verbs.join('+').localeCompare(right.verbs.join('+'))
    || left.handler.localeCompare(right.handler);
}

function renderOperation(operation) {
  const method = operation.verbs.join(' + ');
  const lines = [
    `### ${markdownCode(method)} ${markdownCode(operation.path)}`,
    '',
    `- Handler: ${markdownCode(`${operation.controller}.${operation.handler}`)}`,
    `- Source: ${sourceMarkdownLink(operation.source)}`,
    `- Java return type: ${markdownCode(operation.java.returnType)}`,
    '',
  ];
  const parameterRows = operation.java.parameters.length
    ? operation.java.parameters.map((parameter) => [
      parameter.binding ?? 'framework',
      parameter.wireName ?? parameter.name ?? '—',
      parameter.type ?? parameter.raw ?? '—',
      parameter.required ? 'declared required' : 'not declared required',
      parameter.defaultValue ?? '—',
      annotationText(parameter.constraints ?? parameter.annotations),
    ])
    : [['—', '—', 'No source-declared parameters', '—', '—', '—']];
  lines.push(markdownTable(
    ['Binding', 'Wire name', 'Java type', 'Required', 'Default', 'Validation/annotations'],
    parameterRows,
  ));
  lines.push('', '<details>', `<summary>Java signature for ${markdownCell(operation.handler)}</summary>`, '');
  lines.push('```java', operation.java.signature, '```', '', '</details>', '');
  return lines.join('\n');
}

function renderSchema(name, schema, headingLevel) {
  const lines = [
    `${'#'.repeat(headingLevel)} ${markdownCode(name)}`,
    '',
    `- Kind: ${markdownCode(schema.kind)}`,
    `- Source: ${sourceMarkdownLink(schema.source)}`,
  ];
  if (schema.values?.length) lines.push(`- Enum values: ${schema.values.map(markdownCode).join(', ')}`);
  lines.push('');

  const fieldRows = schema.fields?.length
    ? schema.fields.map((field) => [
      field.name,
      field.type,
      field.required ? 'declared required' : 'not declared required',
      annotationText(field.constraints ?? field.annotations),
    ])
    : [['—', 'No source-declared fields', '—', '—']];
  lines.push(markdownTable(['Field', 'Java type', 'Required', 'Validation/annotations'], fieldRows), '');

  for (const [nestedName, nestedSchema] of Object.entries(schema.nestedTypes ?? {})) {
    lines.push(renderSchema(`${name}.${nestedName}`, nestedSchema, Math.min(headingLevel + 1, 6)));
  }
  return lines.join('\n');
}

function renderCatalog(value) {
  const operationsByService = new Map();
  for (const operation of [...value.operations].sort(operationSort)) {
    const operations = operationsByService.get(operation.service) ?? [];
    operations.push(operation);
    operationsByService.set(operation.service, operations);
  }
  const lines = [
    '# Source-derived HTTP Contract Catalog',
    '',
    '> Generated by `generate-http-contract.mjs`; do not edit by hand. This is a',
    '> human-readable projection of the source-derived manifest, not inferred OpenAPI',
    '> or JSON Schema. Read [API Contract Guide](README.md) for edge classification,',
    '> error semantics and compatibility rules.',
    '',
    `Current inventory: **${value.counts.operations} operations** across **${value.counts.services} controller-owning services** and **${value.counts.sourceSchemas} reachable source schemas**.`,
    '',
    '## Service index',
    '',
    markdownTable(
      ['Service', 'Mapped controller operations'],
      [...operationsByService.entries()]
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([service, operations]) => [service, operations.length]),
    ),
    '',
    '## Operations by service',
    '',
  ];
  for (const [service, operations] of [...operationsByService.entries()]
    .sort(([left], [right]) => left.localeCompare(right))) {
    lines.push(`## ${markdownCode(service)}`, '');
    for (const operation of operations) lines.push(renderOperation(operation));
  }

  lines.push('## Reachable source schemas', '');
  for (const [schemaName, schema] of Object.entries(value.schemas)) {
    lines.push(renderSchema(schemaName, schema, 3));
  }
  return `${lines.join('\n')}\n`;
}

const catalogContent = renderCatalog(contract);
const generatedOutputs = [
  { path: outputPath, content },
  { path: catalogPath, content: catalogContent },
];
if (write) {
  for (const output of generatedOutputs) fs.writeFileSync(output.path, output.content);
  console.log(
    `Wrote ${relative(outputPath)} and ${relative(catalogPath)} with ${contract.counts.operations} operations and ${contract.counts.sourceSchemas} source schemas.`,
  );
} else if (generatedOutputs.some((output) => !fs.existsSync(output.path)
    || fs.readFileSync(output.path, 'utf8') !== output.content)) {
  console.error(`HTTP contract manifest or Markdown catalog is stale or missing; run: node docs/system/api/generate-http-contract.mjs --write`);
  process.exit(1);
} else {
  console.log(
    `PASS: ${relative(outputPath)} and ${relative(catalogPath)} have ${contract.counts.operations} operations and ${contract.counts.sourceSchemas} source schemas.`,
  );
}
