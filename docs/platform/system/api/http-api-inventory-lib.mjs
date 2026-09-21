const mappingAnnotation = /@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\b/g;

function lineAt(source, index) {
  return source.slice(0, index).split(/\r?\n/).length;
}

function maskCommentsAndStrings(source) {
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
    if (current === '\\') {
      characters[index] = ' ';
      if (characters[index + 1] !== '\n') characters[index + 1] = ' ';
      index += 1;
    } else if ((state === 'string' && current === '"')
        || (state === 'character' && current === "'")) {
      characters[index] = ' ';
      state = 'normal';
    } else if (current !== '\n') {
      characters[index] = ' ';
    }
  }
  return characters.join('');
}

function matchingParen(source, openIndex) {
  let depth = 0;
  for (let index = openIndex; index < source.length; index += 1) {
    if (source[index] === '(') depth += 1;
    else if (source[index] === ')') {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  throw new Error(`unbalanced annotation parentheses near line ${lineAt(source, openIndex)}`);
}

function skipWhitespace(source, start) {
  let index = start;
  while (/\s/.test(source[index] ?? '')) index += 1;
  return index;
}

function skipAnnotation(source, start) {
  const name = source.slice(start).match(/^@[A-Za-z_$][\w.$]*/)?.[0];
  if (!name) return start;
  let index = skipWhitespace(source, start + name.length);
  if (source[index] === '(') index = matchingParen(source, index) + 1;
  return index;
}

function nextDeclaration(source, start) {
  let index = skipWhitespace(source, start);
  while (source[index] === '@') {
    index = skipWhitespace(source, skipAnnotation(source, index));
  }
  const declarationStart = index;
  let parenDepth = 0;
  for (; index < source.length; index += 1) {
    const character = source[index];
    if (character === '(') parenDepth += 1;
    else if (character === ')') parenDepth = Math.max(0, parenDepth - 1);
    else if ((character === '{' || character === ';') && parenDepth === 0) {
      return source.slice(declarationStart, index);
    }
  }
  return source.slice(declarationStart);
}

export function extractMappedHandlersFromJava(source, context) {
  const sanitized = maskCommentsAndStrings(source);
  const handlers = [];
  mappingAnnotation.lastIndex = 0;
  let match;
  while ((match = mappingAnnotation.exec(sanitized)) !== null) {
    let annotationEnd = skipWhitespace(sanitized, match.index + match[0].length);
    if (sanitized[annotationEnd] === '(') annotationEnd = matchingParen(sanitized, annotationEnd) + 1;
    const declaration = nextDeclaration(sanitized, annotationEnd);
    if (/\b(class|interface|record|enum)\b/.test(declaration)) continue;
    const openParen = declaration.indexOf('(');
    if (openParen < 0) continue;
    const prefix = declaration.slice(0, openParen);
    if (!/\bpublic\b/.test(prefix)) continue;
    const handler = prefix.match(/([A-Za-z_$][\w$]*)\s*$/)?.[1];
    if (!handler) {
      throw new Error(`cannot resolve mapped handler in ${context.file}:${lineAt(source, match.index)}`);
    }
    handlers.push({
      ...context,
      handler,
      annotation: match[1],
      line: lineAt(source, match.index),
    });
  }
  return handlers;
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

function inventoryKey(row) {
  return `${row.service}|${row.controller}|${row.handler}`;
}

export function parseHttpInventory(markdown) {
  const heading = '## Exact method inventory';
  const start = markdown.indexOf(heading);
  if (start < 0) throw new Error(`missing '${heading}'`);
  const rows = [];
  const keys = new Set();
  for (const line of markdown.slice(start).split(/\r?\n/)) {
    if (!line.startsWith('|')) continue;
    const cells = splitMarkdownRow(line);
    if (cells[0] === 'Service' || /^-+$/.test(cells[0] ?? '')) continue;
    if (!(cells[0] ?? '').replaceAll('`', '').endsWith('-service')) continue;
    if (cells.length !== 5) throw new Error(`inventory row must have 5 cells: ${line}`);
    const [service, controller, verb, routePath, handler] = cells
      .map((cell) => cell.replaceAll('`', '').trim());
    if (!/^[A-Za-z_$][\w$]*$/.test(handler)) throw new Error(`invalid handler '${handler}'`);
    const verbs = verb.split('|').map((item) => item.trim()).filter(Boolean);
    if (verbs.length === 0 || verbs.some((item) => !/^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS|ANY)$/.test(item))) {
      throw new Error(`invalid HTTP verb '${verb}' for ${service}|${controller}|${handler}`);
    }
    const row = { service, controller, verbs, path: routePath, handler };
    const key = inventoryKey(row);
    if (keys.has(key)) throw new Error(`duplicate handler key '${key}'`);
    keys.add(key);
    rows.push(row);
  }
  return rows;
}

export function compareInventoryToSource(inventoryRows, sourceHandlers) {
  const inventory = new Set(inventoryRows.map(inventoryKey));
  const source = new Set(sourceHandlers.map(inventoryKey));
  return {
    missingFromInventory: [...source].filter((key) => !inventory.has(key)).sort(),
    missingFromSource: [...inventory].filter((key) => !source.has(key)).sort(),
  };
}
