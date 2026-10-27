/**
 * Task 1B-Editor Verification Suite: Interactive Note Editor Compatibility & Voice Color Contract
 * Tests live JavaScript functions extracted directly from interactive_editor.html.
 */

const fs = require('fs');
const assert = require('assert');
const path = require('path');
const vm = require('vm');

const EDITOR_PATH = path.resolve(__dirname, '../../../mobile/app/src/main/assets/interactive_editor.html');

console.log('='.repeat(70));
console.log('STARTING TASK 1B-EDITOR: INTERACTIVE EDITOR COMPATIBILITY TEST SUITE');
console.log('Target file:', EDITOR_PATH);
console.log('='.repeat(70));

// 1. Verify file exists and read content
assert(fs.existsSync(EDITOR_PATH), `Target file does not exist: ${EDITOR_PATH}`);
const htmlContent = fs.readFileSync(EDITOR_PATH, 'utf8');

// 2. Syntax Validation: Extract all <script> blocks and verify JavaScript syntax
console.log('\n--- Test 0: JavaScript Syntax & Script Integrity Check ---');
const scriptMatches = htmlContent.match(/<script[\s\S]*?<\/script>/gi);
assert(scriptMatches && scriptMatches.length > 0, 'No script tags found in interactive_editor.html');

let extractedScripts = '';
for (const tag of scriptMatches) {
    const code = tag.replace(/<\/?script[^>]*>/gi, '');
    extractedScripts += code + '\n;\n';
}

// Function constructor syntax check (will throw SyntaxError if syntax is invalid)
try {
    new Function(extractedScripts);
    console.log('[PASS] Test 0: Full interactive_editor.html script block compiles with zero syntax errors.');
} catch (err) {
    console.error('[FAIL] Test 0: Syntax error in interactive_editor.html:', err.message);
    process.exit(1);
}

// 3. Extract getVocalPartColor function from interactive_editor.html
const fnMatch = htmlContent.match(/function getVocalPartColor\([\s\S]*?\n        \}/);
assert(fnMatch, 'Could not locate function getVocalPartColor in interactive_editor.html');

// Create sandbox context with a mock xmlDoc
const context = {
    xmlDoc: null,
    console: console,
    parseInt: parseInt,
    String: String
};

vm.createContext(context);
vm.runInContext(fnMatch[0], context);
const getVocalPartColor = context.getVocalPartColor;
assert(typeof getVocalPartColor === 'function', 'getVocalPartColor was not extracted as a function');

let totalTests = 0;
let passedTests = 0;

function runTest(name, fn) {
    totalTests++;
    try {
        fn();
        passedTests++;
        console.log(`[PASS] ${name}`);
    } catch (err) {
        console.error(`[FAIL] ${name}: ${err.message}`);
    }
}

// --- Test Section 1: Priority 1 Explicit Overrides & data-vx-voice Tokens ---
console.log('\n--- Section 1: Priority 1 Explicit Overrides & data-vx-voice Tokens ---');

runTest('1.1 Soprano Primary and Divisi Overrides', () => {
    assert.strictEqual(getVocalPartColor(1, 0, 0, 4, null, 'S'), '#E11D48');
    assert.strictEqual(getVocalPartColor(1, 0, 0, 4, null, 'SOPRANO'), '#E11D48');
    assert.strictEqual(getVocalPartColor(1, 0, 0, 4, null, '1'), '#E11D48');
    assert.strictEqual(getVocalPartColor(1, 0, 0, 4, null, 'S1'), '#FB7185');
    assert.strictEqual(getVocalPartColor(1, 0, 0, 4, null, 'SOPRANO1'), '#FB7185');
    assert.strictEqual(getVocalPartColor(2, 0, 0, 4, null, 'S2'), '#881337');
    assert.strictEqual(getVocalPartColor(2, 0, 0, 4, null, 'SOPRANO2'), '#881337');
});

runTest('1.2 Alto Primary and Divisi Overrides', () => {
    assert.strictEqual(getVocalPartColor(2, 0, 1, 4, null, 'A'), '#8E24AA');
    assert.strictEqual(getVocalPartColor(2, 0, 1, 4, null, 'ALTO'), '#8E24AA');
    assert.strictEqual(getVocalPartColor(2, 0, 1, 4, null, '2'), '#8E24AA');
    assert.strictEqual(getVocalPartColor(1, 0, 1, 4, null, 'A1'), '#C084FC');
    assert.strictEqual(getVocalPartColor(2, 0, 1, 4, null, 'A2'), '#4C1D95');
});

runTest('1.3 Tenor Primary and Divisi Overrides', () => {
    assert.strictEqual(getVocalPartColor(3, 1, 2, 4, null, 'T'), '#2563EB');
    assert.strictEqual(getVocalPartColor(3, 1, 2, 4, null, 'TENOR'), '#2563EB');
    assert.strictEqual(getVocalPartColor(3, 1, 2, 4, null, '3'), '#2563EB');
    assert.strictEqual(getVocalPartColor(1, 1, 2, 4, null, 'T1'), '#0284C7');
    assert.strictEqual(getVocalPartColor(2, 1, 2, 4, null, 'T2'), '#1E3A8A');
});

runTest('1.4 Bass Primary and Divisi Overrides', () => {
    assert.strictEqual(getVocalPartColor(4, 1, 3, 4, null, 'B'), '#16A34A');
    assert.strictEqual(getVocalPartColor(4, 1, 3, 4, null, 'BASS'), '#16A34A');
    assert.strictEqual(getVocalPartColor(4, 1, 3, 4, null, '4'), '#16A34A');
    assert.strictEqual(getVocalPartColor(1, 1, 3, 4, null, 'B1'), '#65A30D');
    assert.strictEqual(getVocalPartColor(2, 1, 3, 4, null, 'B2'), '#064E3B');
});

runTest('1.5 Unisons, Tutti, Solo, and Others Overrides', () => {
    assert.strictEqual(getVocalPartColor(1, 1, 1, 2, null, 'UNISON_TB'), '#0D9488');
    assert.strictEqual(getVocalPartColor(1, 1, 1, 2, null, 'TB'), '#0D9488');
    assert.strictEqual(getVocalPartColor(1, 0, 0, 2, null, 'UNISON_SA'), '#4F46E5');
    assert.strictEqual(getVocalPartColor(1, 0, 0, 2, null, 'SA'), '#4F46E5');
    assert.strictEqual(getVocalPartColor(1, 0, 0, 4, null, 'TUTTI'), '#CA8A04');
    assert.strictEqual(getVocalPartColor(1, 0, 4, 5, null, 'SOLO'), '#FF6D00');
    assert.strictEqual(getVocalPartColor(1, 0, 4, 5, null, 'BARITONE SOLO'), '#FF6D00');
    assert.strictEqual(getVocalPartColor(1, 0, 4, 5, null, 'BARITONE_SOLO'), '#FF6D00');
    assert.strictEqual(getVocalPartColor(1, 0, 4, 5, null, 'BARITONE'), '#FF6D00');
    assert.strictEqual(getVocalPartColor(1, 0, 4, 5, null, '5'), '#FF6D00');
    assert.strictEqual(getVocalPartColor(1, 0, 5, 6, null, 'OTHERS'), '#546E7A');
    assert.strictEqual(getVocalPartColor(1, 0, 5, 6, null, '6'), '#546E7A');
});

// --- Test Section 2: Priority 2 5-Part Choral Score (Awit ng Paghahangad Pattern) ---
console.log('\n--- Section 2: 5-Part Choral Score (Awit ng Paghahangad Pattern) ---');

const mockAwitDoc = {
    getElementsByTagName: (tag) => {
        if (tag === 'part') {
            return [
                { getAttribute: () => 'P1', querySelectorAll: () => [] },
                { getAttribute: () => 'P2', querySelectorAll: () => [] },
                { getAttribute: () => 'P3', querySelectorAll: () => [] },
                { getAttribute: () => 'P4', querySelectorAll: () => [] },
                { getAttribute: () => 'P5', querySelectorAll: () => [] }
            ];
        }
        return [];
    },
    querySelector: (sel) => {
        if (sel === "score-part[id='P1']") {
            return { querySelector: (q) => ({ textContent: q === 'part-name' ? 'Soprano' : 'S.' }) };
        }
        if (sel === "score-part[id='P2']") {
            return { querySelector: (q) => ({ textContent: q === 'part-name' ? 'Alto' : 'A.' }) };
        }
        if (sel === "score-part[id='P3']") {
            return { querySelector: (q) => ({ textContent: q === 'part-name' ? 'Tenor' : 'T.' }) };
        }
        if (sel === "score-part[id='P4']") {
            return { querySelector: (q) => ({ textContent: q === 'part-name' ? 'Bass' : 'B.' }) };
        }
        if (sel === "score-part[id='P5']") {
            return { querySelector: (q) => ({ textContent: q === 'part-name' ? 'Baritone Solo' : 'Solo' }) };
        }
        return null;
    }
};

runTest('2.1 Awit ng Paghahangad: Part 0 (Soprano) renders Crimson Rose without ReferenceError', () => {
    context.xmlDoc = mockAwitDoc;
    const clr = getVocalPartColor(1, 0, 0, 5, 'top', null);
    assert.strictEqual(clr, '#E11D48', 'Expected Soprano Crimson Rose');
});

runTest('2.2 Awit ng Paghahangad: Part 1 (Alto) renders Royal Purple', () => {
    context.xmlDoc = mockAwitDoc;
    const clr = getVocalPartColor(2, 0, 1, 5, 'bottom', null);
    assert.strictEqual(clr, '#8E24AA', 'Expected Alto Royal Purple');
});

runTest('2.3 Awit ng Paghahangad: Part 2 (Tenor) renders Royal Cobalt Blue', () => {
    context.xmlDoc = mockAwitDoc;
    const clr = getVocalPartColor(3, 1, 2, 5, 'top', null);
    assert.strictEqual(clr, '#2563EB', 'Expected Tenor Royal Cobalt Blue');
});

runTest('2.4 Awit ng Paghahangad: Part 3 (Bass) renders Kelly Emerald Green', () => {
    context.xmlDoc = mockAwitDoc;
    const clr = getVocalPartColor(4, 1, 3, 5, 'bottom', null);
    assert.strictEqual(clr, '#16A34A', 'Expected Bass Kelly Emerald Green');
});

runTest('2.5 Awit ng Paghahangad: Part 4 (Baritone Solo) renders Vivid Tangerine Orange', () => {
    context.xmlDoc = mockAwitDoc;
    const clr = getVocalPartColor(1, 0, 4, 5, 'top', null);
    assert.strictEqual(clr, '#FF6D00', 'Expected Solo Vivid Tangerine Orange');
});

// --- Test Section 3: Open 4-Part SATB (Happy Birthday Pattern) ---
console.log('\n--- Section 3: Open 4-Part SATB (Happy Birthday Pattern) ---');

const mockHbDoc = {
    getElementsByTagName: (tag) => {
        if (tag === 'part') {
            return [
                { getAttribute: () => 'P1', querySelectorAll: () => [] },
                { getAttribute: () => 'P2', querySelectorAll: () => [] },
                { getAttribute: () => 'P3', querySelectorAll: () => [] },
                { getAttribute: () => 'P4', querySelectorAll: () => [] }
            ];
        }
        return [];
    },
    querySelector: (sel) => {
        if (sel === "score-part[id='P1']") return { querySelector: () => ({ textContent: 'Soprano' }) };
        if (sel === "score-part[id='P2']") return { querySelector: () => ({ textContent: 'Alto' }) };
        if (sel === "score-part[id='P3']") return { querySelector: () => ({ textContent: 'Tenor' }) };
        if (sel === "score-part[id='P4']") return { querySelector: () => ({ textContent: 'Bass' }) };
        return null;
    }
};

runTest('3.1 Open SATB: All 4 parts map to distinct SATB colors', () => {
    context.xmlDoc = mockHbDoc;
    assert.strictEqual(getVocalPartColor(1, 0, 0, 4, 'top', null), '#E11D48');
    assert.strictEqual(getVocalPartColor(1, 0, 1, 4, 'top', null), '#8E24AA');
    assert.strictEqual(getVocalPartColor(1, 0, 2, 4, 'top', null), '#2563EB');
    assert.strictEqual(getVocalPartColor(1, 0, 3, 4, 'top', null), '#16A34A');
});

// --- Test Section 4: Condensed 2-Staff Choral Scores (Lead Me Lord Pattern) ---
console.log('\n--- Section 4: Condensed 2-Staff Choral Scores (Lead Me Lord Pattern) ---');

const mockCondensedDoc = {
    getElementsByTagName: (tag) => {
        if (tag === 'part') {
            return [
                { getAttribute: () => 'P1', querySelectorAll: () => [] },
                { getAttribute: () => 'P2', querySelectorAll: () => [] }
            ];
        }
        return [];
    },
    querySelector: (sel) => {
        if (sel === "score-part[id='P1']") return { querySelector: () => ({ textContent: 'Soprano / Alto' }) };
        if (sel === "score-part[id='P2']") return { querySelector: () => ({ textContent: 'Tenor / Bass' }) };
        return null;
    }
};

runTest('4.1 Condensed S/A staff: voice 1 is Soprano, voice 2 is Alto', () => {
    context.xmlDoc = mockCondensedDoc;
    assert.strictEqual(getVocalPartColor(1, 0, 0, 2, 'top', null), '#E11D48');
    assert.strictEqual(getVocalPartColor(2, 0, 0, 2, 'bottom', null), '#8E24AA');
});

runTest('4.2 Condensed T/B staff: voice 1 is Tenor, voice 2 is Bass', () => {
    context.xmlDoc = mockCondensedDoc;
    assert.strictEqual(getVocalPartColor(1, 1, 1, 2, 'top', null), '#2563EB');
    assert.strictEqual(getVocalPartColor(2, 1, 1, 2, 'bottom', null), '#16A34A');
});

// --- Test Summary ---
console.log('\n' + '='.repeat(70));
console.log(`TASK 1B-EDITOR TEST SUMMARY: ${passedTests}/${totalTests} TESTS PASSED`);
console.log('='.repeat(70));

if (passedTests === totalTests) {
    console.log('[SUCCESS] All interactive_editor.html vocal part coloring contracts verified.');
    process.exit(0);
} else {
    console.error(`[FAILURE] ${totalTests - passedTests} tests failed.`);
    process.exit(1);
}
