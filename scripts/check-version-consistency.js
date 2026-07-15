#!/usr/bin/env node
/*
 * S39 / D4 — version-drift guard.
 *
 * Asserts the module version is identical across the three sources that currently drift:
 *   - pom.xml            (<version> of THIS artifact, not the parent)
 *   - package.json       ("version")
 *   - AGENTS.md          ("version": `x.y.z`)
 *
 * Exits 1 when they diverge. RED today (2.0.4 / 2.0.1 / 2.0.2); Stage 7 aligns them.
 * Plain Node, no dependencies. Run with:  node scripts/check-version-consistency.js
 * (also wired as the `check:versions` npm script).
 */
'use strict';

const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');

function read(file) {
    return fs.readFileSync(path.join(root, file), 'utf8');
}

// pom.xml: the module's own <version> is the first <version> AFTER the closing </parent> tag.
function pomVersion() {
    const pom = read('pom.xml');
    const afterParent = pom.slice(pom.indexOf('</parent>'));
    const m = afterParent.match(/<version>([^<]+)<\/version>/);
    return m ? m[1].trim() : null;
}

function packageVersion() {
    return JSON.parse(read('package.json')).version;
}

// AGENTS.md: "**version**: `2.0.2-SNAPSHOT`"
function agentsVersion() {
    const m = read('AGENTS.md').match(/\*\*version\*\*:\s*`?([0-9][^`\s]*)`?/);
    return m ? m[1].trim() : null;
}

const versions = {
    'pom.xml': pomVersion(),
    'package.json': packageVersion(),
    'AGENTS.md': agentsVersion()
};

const distinct = [...new Set(Object.values(versions))];

console.log('Module versions found:');
for (const [source, version] of Object.entries(versions)) {
    console.log(`  ${source.padEnd(14)} ${version}`);
}

if (distinct.length === 1) {
    console.log(`\nOK — all sources agree on ${distinct[0]}`);
    process.exit(0);
}

console.error(`\nFAIL — version drift across sources: ${distinct.join(', ')}`);
process.exit(1);
