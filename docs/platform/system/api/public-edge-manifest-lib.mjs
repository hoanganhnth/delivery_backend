const rateLimitSource = 'api-gateway/src/main/java/com/delivery/api_gateway/ratelimit/GatewayRateLimitFilter.java';

function lineAt(source, index) {
  return source.slice(0, index).split(/\r?\n/).length;
}

function maskJava(source, { strings }) {
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
        if (strings) characters[index] = ' ';
        state = 'string';
      } else if (current === "'") {
        if (strings) characters[index] = ' ';
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
      if (strings) characters[index] = ' ';
      if (strings && characters[index + 1] !== '\n') characters[index + 1] = ' ';
      index += 1;
    } else if ((state === 'string' && current === '"')
        || (state === 'character' && current === "'")) {
      if (strings) characters[index] = ' ';
      state = 'normal';
    } else if (strings && current !== '\n') {
      characters[index] = ' ';
    }
  }
  return characters.join('');
}

function matchingDelimiter(source, openIndex, open, close) {
  let depth = 0;
  let quote = null;
  let escaped = false;
  for (let index = openIndex; index < source.length; index += 1) {
    const character = source[index];
    if (quote) {
      if (escaped) escaped = false;
      else if (character === '\\') escaped = true;
      else if (character === quote) quote = null;
      continue;
    }
    if (character === '"' || character === "'") {
      quote = character;
      continue;
    }
    if (character === open) depth += 1;
    else if (character === close) {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  throw new Error(`unbalanced '${open}${close}' near line ${lineAt(source, openIndex)}`);
}

function skipWhitespace(source, start) {
  let index = start;
  while (/\s/.test(source[index] ?? '')) index += 1;
  return index;
}

function parseStringArguments(value, description) {
  const strings = [];
  let remainder = '';
  let cursor = 0;
  const matcher = /"(?:\\.|[^"\\])*"/g;
  for (const match of value.matchAll(matcher)) {
    remainder += value.slice(cursor, match.index);
    strings.push(JSON.parse(match[0]));
    cursor = match.index + match[0].length;
  }
  remainder += value.slice(cursor);
  if (strings.length === 0 || remainder.replaceAll(',', '').trim()) {
    throw new Error(`${description} must contain only string literal arguments`);
  }
  return strings;
}

function parseConstructorValues(source) {
  const values = new Map();
  const matcher = /@Value\s*\(\s*"\$\{([^:}]+):([^}]*)\}"\s*\)\s+(String|boolean)\s+([A-Za-z_$][\w$]*)/g;
  for (const match of source.matchAll(matcher)) {
    values.set(match[4], {
      variable: match[4],
      property: match[1],
      defaultValue: match[2],
      type: match[3],
    });
  }
  return values;
}

function parseIfBlocks(codeOnly, commentsOnly) {
  const blocks = [];
  const matcher = /\bif\s*\(\s*([A-Za-z_$][\w$]*)\s*\)\s*\{/g;
  for (const match of codeOnly.matchAll(matcher)) {
    const open = codeOnly.indexOf('{', match.index);
    blocks.push({
      variable: match[1],
      open,
      close: matchingDelimiter(commentsOnly, open, '{', '}'),
    });
  }
  return blocks;
}

function parseRewrite(argument, routeId) {
  const prefix = argument.match(/^\s*([A-Za-z_$][\w$]*)\s*->\s*\1\.rewritePath\s*\(/);
  if (!prefix) throw new Error(`route '${routeId}' has an unsupported filters(...) body`);
  const open = argument.indexOf('(', prefix.index + prefix[0].length - 1);
  const close = matchingDelimiter(argument, open, '(', ')');
  if (argument.slice(close + 1).trim()) {
    throw new Error(`route '${routeId}' has more than one or an unsupported filter`);
  }
  const strings = parseStringArguments(argument.slice(open + 1, close), `route '${routeId}' rewritePath`);
  if (strings.length !== 2) throw new Error(`route '${routeId}' rewritePath must have two arguments`);
  return { from: strings[0], to: strings[1] };
}

function parseChain(chain, routeId) {
  const calls = [];
  let index = 0;
  while ((index = skipWhitespace(chain, index)) < chain.length) {
    if (chain[index] !== '.') throw new Error(`route '${routeId}' contains unsupported syntax near '${chain.slice(index, index + 30).trim()}'`);
    const nameMatch = chain.slice(index + 1).match(/^([A-Za-z_$][\w$]*)/);
    if (!nameMatch) throw new Error(`route '${routeId}' has an invalid route-chain method`);
    const name = nameMatch[1];
    let open = skipWhitespace(chain, index + 1 + name.length);
    if (chain[open] !== '(') throw new Error(`route '${routeId}' method '${name}' is not a call`);
    const close = matchingDelimiter(chain, open, '(', ')');
    calls.push({ name, argument: chain.slice(open + 1, close) });
    index = close + 1;
  }

  const allowed = new Set(['path', 'and', 'method', 'filters', 'uri']);
  for (const call of calls) {
    if (!allowed.has(call.name)) throw new Error(`unsupported route-chain method '${call.name}' in route '${routeId}'`);
  }
  const byName = (name) => calls.filter((call) => call.name === name);
  if (byName('path').length !== 1) throw new Error(`route '${routeId}' must have exactly one path(...) call`);
  if (byName('uri').length !== 1) throw new Error(`route '${routeId}' must have exactly one uri(...) call`);
  if (byName('method').length > 1 || byName('filters').length > 1) {
    throw new Error(`route '${routeId}' repeats method(...) or filters(...)`);
  }
  for (const andCall of byName('and')) {
    if (andCall.argument.trim()) throw new Error(`route '${routeId}' has unsupported and(...) arguments`);
  }

  const paths = parseStringArguments(byName('path')[0].argument, `route '${routeId}' path`);
  const methodCall = byName('method')[0];
  const methods = methodCall
    ? methodCall.argument.split(',').map((item) => {
      const match = item.trim().match(/^HttpMethod\.(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)$/);
      if (!match) throw new Error(`route '${routeId}' has unsupported HTTP method '${item.trim()}'`);
      return match[1];
    })
    : ['ANY'];
  const uriVariable = byName('uri')[0].argument.trim();
  if (!/^[A-Za-z_$][\w$]*$/.test(uriVariable)) {
    throw new Error(`route '${routeId}' URI must be a configured variable`);
  }
  const filterCall = byName('filters')[0];
  return {
    paths,
    methods,
    uriVariable,
    rewrite: filterCall ? parseRewrite(filterCall.argument, routeId) : undefined,
  };
}

function downstreamService(defaultUri) {
  const loadBalanced = defaultUri.match(/^lb:(?:ws:)?\/\/([^/]+)$/);
  if (loadBalanced) return loadBalanced[1];
  try {
    return new URL(defaultUri).hostname;
  } catch {
    throw new Error(`unsupported downstream URI '${defaultUri}'`);
  }
}

export function parseGatewayRouteSource(source, options = {}) {
  const file = options.file ?? 'GatewayRouteConfig.java';
  const commentsOnly = maskJava(source, { strings: false });
  const codeOnly = maskJava(source, { strings: true });
  const constructorValues = parseConstructorValues(commentsOnly);
  const ifBlocks = parseIfBlocks(codeOnly, commentsOnly);
  const routeMatcher = /\.route\s*\(/g;
  const routes = [];
  const routeIds = new Set();

  for (const match of codeOnly.matchAll(routeMatcher)) {
    const open = codeOnly.indexOf('(', match.index);
    const close = matchingDelimiter(commentsOnly, open, '(', ')');
    const content = commentsOnly.slice(open + 1, close);
    const header = content.match(/^\s*"((?:\\.|[^"\\])*)"\s*,\s*([A-Za-z_$][\w$]*)\s*->\s*\2/);
    if (!header) throw new Error(`cannot parse route declaration at ${file}:${lineAt(source, match.index)}`);
    const id = JSON.parse(`"${header[1]}"`);
    if (routeIds.has(id)) throw new Error(`duplicate route ID '${id}'`);
    routeIds.add(id);
    const chain = parseChain(content.slice(header[0].length), id);
    const destination = constructorValues.get(chain.uriVariable);
    if (!destination || destination.type !== 'String') {
      throw new Error(`route '${id}' URI variable '${chain.uriVariable}' has no String @Value binding`);
    }

    const enclosingGates = ifBlocks.filter((block) => match.index > block.open && match.index < block.close);
    if (enclosingGates.length > 1) throw new Error(`route '${id}' is inside multiple feature gates`);
    let featureGate = { mode: 'always' };
    if (enclosingGates.length === 1) {
      const gate = constructorValues.get(enclosingGates[0].variable);
      if (!gate || gate.type !== 'boolean' || !/^(true|false)$/.test(gate.defaultValue)) {
        throw new Error(`route '${id}' feature gate '${enclosingGates[0].variable}' has no boolean @Value binding`);
      }
      featureGate = {
        mode: 'property',
        variable: gate.variable,
        property: gate.property,
        default: gate.defaultValue === 'true',
      };
    }

    const route = {
      id,
      paths: chain.paths,
      methods: chain.methods,
      downstream: {
        service: downstreamService(destination.defaultValue),
        uriVariable: chain.uriVariable,
        property: destination.property,
        defaultUri: destination.defaultValue,
      },
      featureGate,
      rateLimit: {
        classification: 'request-dependent',
        source: rateLimitSource,
      },
      source: { file, line: lineAt(source, match.index) },
    };
    if (chain.rewrite) route.rewrite = chain.rewrite;
    routes.push(route);
  }

  routes.sort((left, right) => left.id.localeCompare(right.id));
  const byGate = {};
  for (const route of routes) {
    const key = route.featureGate.mode === 'always' ? 'always' : route.featureGate.property;
    byGate[key] = (byGate[key] ?? 0) + 1;
  }
  return {
    counts: {
      routes: routes.length,
      pathPatterns: routes.reduce((total, route) => total + route.paths.length, 0),
      byGate: Object.fromEntries(Object.entries(byGate).sort(([left], [right]) => left.localeCompare(right))),
    },
    routes,
  };
}
