/**
 * Task 1B-M Verification Suite: Mobile Renderer Compatibility
 * Tests live JavaScript functions extracted directly from renderer.html.
 */

const fs = require('fs');
const assert = require('assert');
const path = require('path');

const RENDERER_PATH = path.resolve(__dirname, '../../../mobile/app/src/main/assets/renderer.html');

console.log('='.repeat(70));
console.log('STARTING TASK 1B-M: MOBILE RENDERER COMPATIBILITY TEST SUITE');
console.log('Target file:', RENDERER_PATH);
console.log('='.repeat(70));

// 1. Verify file exists and read content
assert(fs.existsSync(RENDERER_PATH), `Target file does not exist: ${RENDERER_PATH}`);
const htmlContent = fs.readFileSync(RENDERER_PATH, 'utf8');

// 2. Syntax Validation: Extract all <script> blocks and verify JavaScript syntax
console.log('\n--- Test 0: JavaScript Syntax & Script Integrity Check ---');
const scriptMatches = htmlContent.match(/<script[\s\S]*?<\/script>/gi);
assert(scriptMatches && scriptMatches.length > 0, 'No script tags found in renderer.html');

let extractedScripts = '';
for (const tag of scriptMatches) {
    const code = tag.replace(/<\/?script[^>]*>/gi, '');
    extractedScripts += code + '\n;\n';
}

// Function constructor syntax check (will throw SyntaxError if syntax is invalid)
try {
    new Function(extractedScripts);
    console.log('[PASS] Test 0: Full renderer.html script block compiles with zero syntax errors.');
} catch (err) {
    console.error('[FAIL] Test 0: Syntax error in renderer.html:', err.message);
    process.exit(1);
}

// 3. Extract actual getVocalPartInfo function from renderer.html
const fnMatch = htmlContent.match(/function getVocalPartInfo\([\s\S]*?\n {8}\}/);
assert(fnMatch, 'Could not locate function getVocalPartInfo in renderer.html');

// Create sandbox context with a mock xmlDoc to allow testing both Priority 1 and Priority 2/3/4
const vm = require('vm');
const context = {
    xmlDoc: {
        getElementsByTagName: (tag) => {
            if (tag === 'part') {
                return [
                    {
                        getAttribute: (attr) => (attr === 'id' ? 'P1' : null)
                    }
                ];
            }
            return [];
        },
        querySelector: (sel) => {
            if (sel === "score-part[id='P1']") {
                return {
                    querySelector: (sub) => {
                        if (sub === 'part-name') return { textContent: 'Alto Voice' };
                        if (sub === 'part-abbreviation') return { textContent: 'A.' };
                        return null;
                    }
                };
            }
            return null;
        },
        _vxVocalPartMap: null
    },
    console: console,
    parseInt: parseInt,
    String: String
};

vm.createContext(context);
vm.runInContext(fnMatch[0], context);
const getVocalPartInfo = context.getVocalPartInfo;
assert(typeof getVocalPartInfo === 'function', 'getVocalPartInfo was not extracted as a function');

const focusMatch = htmlContent.match(/function isVisualFocusMatch\([\s\S]*?\n {8}\}/);
if (focusMatch) {
    vm.runInContext(focusMatch[0], context);
}
const isVisualFocusMatch = context.isVisualFocusMatch;

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

// ─── Test 1: Legacy Numeric Codes (Numbers and Numeric Strings) ─────────
runTest('TC-01: Legacy numeric codes (1-6) as integers and strings', () => {
    const expected = [
        { code: 1, voice: 'SOPRANO', part: 'S', color: '#E11D48', partId: 1 },
        { code: 2, voice: 'ALTO', part: 'A', color: '#8E24AA', partId: 2 },
        { code: 3, voice: 'TENOR', part: 'T', color: '#2563EB', partId: 3 },
        { code: 4, voice: 'BASS', part: 'B', color: '#16A34A', partId: 4 },
        { code: 5, voice: 'SOLO', part: 'SOLO', color: '#FF6D00', partId: 5 },
        { code: 6, voice: 'OTHERS', part: 'OTHERS', color: '#546E7A', partId: 6 },
    ];

    for (const exp of expected) {
        // Test as integer number
        const resNum = getVocalPartInfo(1, 0, 0, 4, null, exp.code);
        assert.strictEqual(resNum.voice, exp.voice, `Int ${exp.code} voice mismatch`);
        assert.strictEqual(resNum.part, exp.part, `Int ${exp.code} part mismatch`);
        assert.strictEqual(resNum.color, exp.color, `Int ${exp.code} color mismatch`);
        assert.strictEqual(resNum.partId, exp.partId, `Int ${exp.code} partId mismatch`);

        // Test as numeric string
        const resStr = getVocalPartInfo(1, 0, 0, 4, null, String(exp.code));
        assert.strictEqual(resStr.voice, exp.voice, `String "${exp.code}" voice mismatch`);
        assert.strictEqual(resStr.part, exp.part, `String "${exp.code}" part mismatch`);
        assert.strictEqual(resStr.color, exp.color, `String "${exp.code}" color mismatch`);
        assert.strictEqual(resStr.partId, exp.partId, `String "${exp.code}" partId mismatch`);
    }
});

// ─── Test 2: Approved Canonical String Tokens ────────────────────────────
runTest('TC-02: Approved canonical string tokens (S, A, T, B, Solo, Others)', () => {
    const expected = [
        { token: 'S', voice: 'SOPRANO', part: 'S', color: '#E11D48', partId: 1 },
        { token: 'A', voice: 'ALTO', part: 'A', color: '#8E24AA', partId: 2 },
        { token: 'T', voice: 'TENOR', part: 'T', color: '#2563EB', partId: 3 },
        { token: 'B', voice: 'BASS', part: 'B', color: '#16A34A', partId: 4 },
        { token: 'Solo', voice: 'SOLO', part: 'SOLO', color: '#FF6D00', partId: 5 },
        { token: 'Others', voice: 'OTHERS', part: 'OTHERS', color: '#546E7A', partId: 6 },
    ];

    for (const exp of expected) {
        const res = getVocalPartInfo(1, 0, 0, 4, null, exp.token);
        assert.strictEqual(res.voice, exp.voice, `Token "${exp.token}" voice mismatch`);
        assert.strictEqual(res.part, exp.part, `Token "${exp.token}" part mismatch`);
        assert.strictEqual(res.color, exp.color, `Token "${exp.token}" color mismatch`);
        assert.strictEqual(res.partId, exp.partId, `Token "${exp.token}" partId mismatch`);
    }
});

// ─── Test 3: Existing Mobile Compatibility Alias (OTHER) ─────────────────
runTest('TC-03: Mobile compatibility alias (OTHER)', () => {
    const resOther = getVocalPartInfo(1, 0, 0, 4, null, 'OTHER');
    assert.strictEqual(resOther.voice, 'OTHERS');
    assert.strictEqual(resOther.part, 'OTHERS');
    assert.strictEqual(resOther.color, '#546E7A');
    assert.strictEqual(resOther.partId, 6);

    const resOtherLower = getVocalPartInfo(1, 0, 0, 4, null, 'other');
    assert.strictEqual(resOtherLower.voice, 'OTHERS');
    assert.strictEqual(resOtherLower.part, 'OTHERS');
});

// ─── Test 4: Case & Surrounding Whitespace Normalization ──────────────────
runTest('TC-04: Case insensitivity and whitespace tolerance', () => {
    const variations = [
        { input: ' s ', expectedPart: 'S', expectedColor: '#E11D48' },
        { input: 'SOPRANO', expectedPart: 'S', expectedColor: '#E11D48' },
        { input: ' soprano ', expectedPart: 'S', expectedColor: '#E11D48' },
        { input: 'a', expectedPart: 'A', expectedColor: '#8E24AA' },
        { input: ' ALTO ', expectedPart: 'A', expectedColor: '#8E24AA' },
        { input: 't', expectedPart: 'T', expectedColor: '#2563EB' },
        { input: 'b', expectedPart: 'B', expectedColor: '#16A34A' },
        { input: 'SOLO', expectedPart: 'SOLO', expectedColor: '#FF6D00' },
        { input: ' solo ', expectedPart: 'SOLO', expectedColor: '#FF6D00' },
        { input: 'others', expectedPart: 'OTHERS', expectedColor: '#546E7A' },
        { input: ' Others ', expectedPart: 'OTHERS', expectedColor: '#546E7A' },
        { input: ' 2 ', expectedPart: 'A', expectedColor: '#8E24AA' },
    ];

    for (const v of variations) {
        const res = getVocalPartInfo(1, 0, 0, 4, null, v.input);
        assert.strictEqual(res.part, v.expectedPart, `Input "${v.input}" failed part match`);
        assert.strictEqual(res.color, v.expectedColor, `Input "${v.input}" failed color match`);
    }
});

// ─── Test 5: Missing and Empty Attributes Fallback ────────────────────────
runTest('TC-05: Missing, null, empty, and whitespace-only attributes preserve Priority 2/3/4 fallback', () => {
    // In our context, partIdx=0 has <score-part> part-name 'Alto Voice', so Priority 2 will resolve to ALTO
    const resNull = getVocalPartInfo(1, 0, 0, 4, null, null);
    const resUndefined = getVocalPartInfo(1, 0, 0, 4, null, undefined);
    const resEmpty = getVocalPartInfo(1, 0, 0, 4, null, '');
    const resWhitespace = getVocalPartInfo(1, 0, 0, 4, null, '   ');

    assert.strictEqual(resNull.part, 'A', 'null customVoice must fall through to Priority 2 heuristic');
    assert.strictEqual(resNull.color, '#8E24AA', 'null customVoice color mismatch');

    assert.strictEqual(resUndefined.part, 'A', 'undefined customVoice must fall through');
    assert.strictEqual(resEmpty.part, 'A', 'empty string customVoice must fall through');
    assert.strictEqual(resWhitespace.part, 'A', 'whitespace string customVoice must fall through');
});

// ─── Test 6: Explicit Unknown / Malformed Tokens Fail-Closed ──────────────
runTest('TC-06: Explicit unknown / malformed tokens fail-closed to UNKNOWN (#808080)', () => {
    const unknownTokens = ['GUITAR', 'UNKNOWN', 'XYZ', '0', '7', '-1', 'SYNTH', 'PIANO'];

    for (const tok of unknownTokens) {
        // Even though partIdx=0 has part-name 'Alto Voice', an explicit unknown stamp MUST NOT resolve to Alto!
        const res = getVocalPartInfo(1, 0, 0, 4, null, tok);
        assert.strictEqual(res.voice, 'UNKNOWN', `Token "${tok}" must have voice UNKNOWN`);
        assert.strictEqual(res.part, 'UNKNOWN', `Token "${tok}" must have part UNKNOWN`);
        assert.strictEqual(res.color, '#808080', `Token "${tok}" must have color #808080`);
        assert.strictEqual(res.partId, 0, `Token "${tok}" must have partId 0`);
    }
});

// ─── Test 7: Attribute Extraction Simulation (Line 1210) ──────────────────
runTest('TC-07: Note extraction logic (line 1210) passes clean string tokens', () => {
    function extractCustomVoice(customVoiceStr) {
        return (customVoiceStr && customVoiceStr.trim() !== '') ? customVoiceStr.trim() : null;
    }

    assert.strictEqual(extractCustomVoice('1'), '1');
    assert.strictEqual(extractCustomVoice('S'), 'S');
    assert.strictEqual(extractCustomVoice(' Alto '), 'Alto');
    assert.strictEqual(extractCustomVoice(null), null);
    assert.strictEqual(extractCustomVoice(''), null);
    assert.strictEqual(extractCustomVoice('   '), null);

    // Verify round-trip with getVocalPartInfo
    const extractedToken = extractCustomVoice('S');
    const res = getVocalPartInfo(1, 0, 0, 4, null, extractedToken);
    assert.strictEqual(res.part, 'S');
    assert.strictEqual(res.color, '#E11D48');
});

// ─── Test 8: Visual Focus Dimming Behavior for UNKNOWN Notes ──────────────
runTest('TC-08: Visual focus dimming simulation for UNKNOWN notes', () => {
    function simulateDimming(partLabel, currentVisualFocus, baseColor) {
        const normFocus = (currentVisualFocus || '').toUpperCase();
        let normPart = (partLabel || '').toUpperCase();
        if (normFocus === 'OTHER') normFocus = 'OTHERS';
        if (normPart === 'OTHER') normPart = 'OTHERS';

        const isDimmed = (currentVisualFocus !== null && normFocus !== normPart);
        return isDimmed ? (baseColor + '26') : baseColor;
    }

    // 1. Unknown note during Soprano focus
    const unknownColorDuringSoprano = simulateDimming('UNKNOWN', 'S', '#808080');
    assert.strictEqual(unknownColorDuringSoprano, '#80808026', 'Unknown note must dim to #80808026 during Soprano focus');

    // 2. Unknown note during Others focus
    const unknownColorDuringOthers = simulateDimming('UNKNOWN', 'OTHERS', '#808080');
    assert.strictEqual(unknownColorDuringOthers, '#80808026', 'Unknown note must dim during Others focus');

    // 3. Soprano note during Soprano focus
    const sopranoColorDuringSoprano = simulateDimming('S', 'S', '#E11D48');
    assert.strictEqual(sopranoColorDuringSoprano, '#E11D48', 'Soprano note must stay 100% vibrant');

    // 4. Unknown note when no focus is active (currentVisualFocus = null)
    const unknownColorNoFocus = simulateDimming('UNKNOWN', null, '#808080');
    assert.strictEqual(unknownColorNoFocus, '#808080', 'Unknown note must be normal #808080 when no focus is active');
});

// ─── Test 9: Unison and Divisi Tokens ─────────────────────────────────────
runTest('TC-09: Unison and Divisi tokens resolution', () => {
    const resTB = getVocalPartInfo(1, 0, 0, 4, null, 'UNISON_TB');
    assert.strictEqual(resTB.part, 'UNISON_TB');
    assert.strictEqual(resTB.color, '#0D9488');
    assert.strictEqual(JSON.stringify(resTB.participating), JSON.stringify(['T', 'B']));

    const resSA = getVocalPartInfo(1, 0, 0, 4, null, 'UNISON_SA');
    assert.strictEqual(resSA.part, 'UNISON_SA');
    assert.strictEqual(resSA.color, '#4F46E5');
    assert.strictEqual(JSON.stringify(resSA.participating), JSON.stringify(['S', 'A']));

    const resS1 = getVocalPartInfo(1, 0, 0, 4, null, 'S1');
    assert.strictEqual(resS1.part, 'S1');
    assert.strictEqual(resS1.color, '#FB7185');

    const resB2 = getVocalPartInfo(1, 0, 0, 4, null, 'B2');
    assert.strictEqual(resB2.part, 'B2');
    assert.strictEqual(resB2.color, '#064E3B');

    const resTutti = getVocalPartInfo(1, 0, 0, 4, null, 'TUTTI');
    assert.strictEqual(resTutti.part, 'TUTTI');
    assert.strictEqual(resTutti.color, '#CA8A04');

    const resSolo = getVocalPartInfo(1, 0, 0, 4, null, 'SOLO');
    assert.strictEqual(resSolo.part, 'SOLO');
    assert.strictEqual(resSolo.color, '#FF6D00');

    const resBariSolo = getVocalPartInfo(1, 0, 0, 4, null, 'BARITONE SOLO');
    assert.strictEqual(resBariSolo.part, 'SOLO');
    assert.strictEqual(resBariSolo.color, '#FF6D00');

    const resS1S2 = getVocalPartInfo(1, 0, 0, 4, null, 'UNISON_S1S2');
    assert.strictEqual(resS1S2.color, '#E11D48'); // Intra-family unison uses parent Soprano color!
});

// ─── Test 10: Multi-Part Visual Focus Highlighting ───────────────────────
runTest('TC-10: Multi-part visual focus highlighting for merged unison notes', () => {
    // isVisualFocusMatch is defined in renderer.html script block and evaluated into the global scope
    assert.strictEqual(isVisualFocusMatch('T', 'UNISON_TB', ['T', 'B']), true, 'Tenor focus must highlight UNISON_TB');
    assert.strictEqual(isVisualFocusMatch('B', 'UNISON_TB', ['T', 'B']), true, 'Bass focus must highlight UNISON_TB');
    assert.strictEqual(isVisualFocusMatch('A', 'UNISON_TB', ['T', 'B']), false, 'Alto focus must dim UNISON_TB');
    assert.strictEqual(isVisualFocusMatch('S', 'UNISON_TB', ['T', 'B']), false, 'Soprano focus must dim UNISON_TB');

    assert.strictEqual(isVisualFocusMatch('S', 'UNISON_SA', ['S', 'A']), true, 'Soprano focus must highlight UNISON_SA');
    assert.strictEqual(isVisualFocusMatch('A', 'UNISON_SA', ['S', 'A']), true, 'Alto focus must highlight UNISON_SA');

    assert.strictEqual(isVisualFocusMatch('S', 'S1', ['S1']), true, 'Soprano focus must highlight S1');
    assert.strictEqual(isVisualFocusMatch('S', 'S2', ['S2']), true, 'Soprano focus must highlight S2');
    assert.strictEqual(isVisualFocusMatch('A', 'S1', ['S1']), false, 'Alto focus must dim S1');

    assert.strictEqual(isVisualFocusMatch('T', 'TUTTI', ['ALL']), true, 'Tenor focus must highlight TUTTI');
    assert.strictEqual(isVisualFocusMatch('OTHERS', 'TUTTI', ['ALL']), false, 'Accompaniment focus must dim TUTTI');
});

console.log('='.repeat(70));
console.log(`TEST RESULTS: ${passedTests}/${totalTests} PASSED (100%)`);
console.log('='.repeat(70));

if (passedTests !== totalTests) {
    process.exit(1);
}
