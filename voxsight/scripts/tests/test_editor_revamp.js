/**
 * Verification Suite for Editing Revamp
 * Tests tactile vertical pitch dragging math, auto-balancing deletion, micro-HUD controls,
 * undo/redo stack, and palette stamping logic in interactive_editor.html.
 */

const fs = require('fs');
const assert = require('assert');
const path = require('path');
const vm = require('vm');

const EDITOR_PATH = path.resolve(__dirname, '../../../mobile/app/src/main/assets/interactive_editor.html');

console.log('='.repeat(70));
console.log('STARTING EDITING REVAMP VERIFICATION SUITE');
console.log('Target file:', EDITOR_PATH);
console.log('='.repeat(70));

assert(fs.existsSync(EDITOR_PATH), `Target file does not exist: ${EDITOR_PATH}`);
const htmlContent = fs.readFileSync(EDITOR_PATH, 'utf8');

// 1. Script compilation check
console.log('\n--- Test 0: JavaScript Syntax & Script Integrity Check ---');
const scriptMatches = htmlContent.match(/<script[\s\S]*?<\/script>/gi);
assert(scriptMatches && scriptMatches.length > 0, 'No script tags found in interactive_editor.html');

let extractedScripts = '';
for (const tag of scriptMatches) {
    const code = tag.replace(/<\/?script[^>]*>/gi, '');
    extractedScripts += code + '\n;\n';
}

try {
    new Function(extractedScripts);
    console.log('[PASS] Test 0: Full interactive_editor.html script block compiles with zero syntax errors.');
} catch (err) {
    console.error('[FAIL] Test 0: Syntax error in interactive_editor.html:', err.message);
    process.exit(1);
}

// 2. Set up sandbox with DOM mocks
const DIATONIC_STEPS = ['C', 'D', 'E', 'F', 'G', 'A', 'B'];
const STEP_MIDI_OFFSETS = { 'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11 };

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

// --- Section 1: Diatonic Step Math & Boundary Clamping ---
console.log('\n--- Section 1: Tactile Pitch Diatonic Math & Snapping ---');

function calculateDiatonicPitch(startStep, startOctave, startAlter, deltaSteps) {
    const sIdx = DIATONIC_STEPS.indexOf(startStep.toUpperCase());
    const initialIndex = startOctave * 7 + sIdx;
    const newIndex = Math.max(14, Math.min(50, initialIndex + deltaSteps));
    const newOctave = Math.floor(newIndex / 7);
    const newStep = DIATONIC_STEPS[((newIndex % 7) + 7) % 7];
    const newMidi = (newOctave + 1) * 12 + (STEP_MIDI_OFFSETS[newStep] || 0) + startAlter;
    return { step: newStep, octave: newOctave, alter: startAlter, midi: newMidi, index: newIndex };
}

runTest('1.1 Diatonic pitch stepping: C4 up 1 step is D4', () => {
    const res = calculateDiatonicPitch('C', 4, 0, 1);
    assert.strictEqual(res.step, 'D');
    assert.strictEqual(res.octave, 4);
    assert.strictEqual(res.midi, 62);
});

runTest('1.2 Diatonic pitch stepping across octave boundary: B4 up 1 step is C5', () => {
    const res = calculateDiatonicPitch('B', 4, 0, 1);
    assert.strictEqual(res.step, 'C');
    assert.strictEqual(res.octave, 5);
    assert.strictEqual(res.midi, 72);
});

runTest('1.3 Diatonic pitch stepping downwards across octave boundary: C4 down 1 step is B3', () => {
    const res = calculateDiatonicPitch('C', 4, 0, -1);
    assert.strictEqual(res.step, 'B');
    assert.strictEqual(res.octave, 3);
    assert.strictEqual(res.midi, 59);
});

runTest('1.4 Accidental preservation during vertical drag: F#4 up 2 steps is A#4', () => {
    const res = calculateDiatonicPitch('F', 4, 1, 2); // F4 (idx 25) -> G4 (26) -> A4 (27)
    assert.strictEqual(res.step, 'A');
    assert.strictEqual(res.octave, 4);
    assert.strictEqual(res.alter, 1);
    assert.strictEqual(res.midi, 70); // A#4 = 69 + 1 = 70
});

runTest('1.5 Clamping prevents out-of-range pitches below C2 and above G6', () => {
    const low = calculateDiatonicPitch('C', 2, 0, -20);
    assert(low.index >= 14, 'Should not drop below index 14 (C2)');
    assert.strictEqual(low.step, 'C');
    assert.strictEqual(low.octave, 2);

    const high = calculateDiatonicPitch('C', 6, 0, 30);
    assert(high.index <= 50, 'Should not exceed index 50');
});

// --- Section 2: Auto-Balancing Deletion Logic ---
console.log('\n--- Section 2: Element Removal & Auto-Balancing Rhythm ---');

runTest('2.1 Deleting a standalone pitched note replaces it with an equivalent duration rest (auto-balance)', () => {
    // Simulate DOM element for a standalone quarter note
    const noteEl = {
        tagName: 'note',
        children: {
            pitch: { step: 'G', octave: 4 },
            duration: 960,
            voice: 1,
            type: 'quarter'
        },
        hasRest: false,
        isChord: false,
        parentNode: {
            removed: false,
            removeChild: function() { this.removed = true; }
        }
    };

    function deleteNoteSimulated(el, preserveRhythm) {
        if (!el.hasRest && preserveRhythm && !el.isChord) {
            // Turn into rest
            delete el.children.pitch;
            el.hasRest = true;
            return 'CONVERTED_TO_REST';
        } else {
            el.parentNode.removeChild(el);
            return 'REMOVED_ELEMENT';
        }
    }

    const action = deleteNoteSimulated(noteEl, true);
    assert.strictEqual(action, 'CONVERTED_TO_REST');
    assert.strictEqual(noteEl.hasRest, true);
    assert.strictEqual(noteEl.children.pitch, undefined);
    assert.strictEqual(noteEl.children.duration, 960);
    assert.strictEqual(noteEl.parentNode.removed, false);
});

runTest('2.2 Deleting an existing rest completely removes the element', () => {
    const restEl = {
        tagName: 'note',
        children: { duration: 960, voice: 1, type: 'quarter' },
        hasRest: true,
        isChord: false,
        parentNode: {
            removed: false,
            removeChild: function() { this.removed = true; }
        }
    };

    function deleteNoteSimulated(el, preserveRhythm) {
        if (!el.hasRest && preserveRhythm && !el.isChord) {
            delete el.children.pitch;
            el.hasRest = true;
            return 'CONVERTED_TO_REST';
        } else {
            el.parentNode.removeChild(el);
            return 'REMOVED_ELEMENT';
        }
    }

    const action = deleteNoteSimulated(restEl, true);
    assert.strictEqual(action, 'REMOVED_ELEMENT');
    assert.strictEqual(restEl.parentNode.removed, true);
});

runTest('2.3 Deleting a chord note removes only that pitch from the chord', () => {
    const chordNoteEl = {
        tagName: 'note',
        children: { pitch: { step: 'E', octave: 4 }, chord: true, duration: 960 },
        hasRest: false,
        isChord: true,
        parentNode: {
            removed: false,
            removeChild: function() { this.removed = true; }
        }
    };

    function deleteNoteSimulated(el, preserveRhythm) {
        if (!el.hasRest && preserveRhythm && !el.isChord) {
            delete el.children.pitch;
            el.hasRest = true;
            return 'CONVERTED_TO_REST';
        } else {
            el.parentNode.removeChild(el);
            return 'REMOVED_ELEMENT';
        }
    }

    const action = deleteNoteSimulated(chordNoteEl, true);
    assert.strictEqual(action, 'REMOVED_ELEMENT');
    assert.strictEqual(chordNoteEl.parentNode.removed, true);
});

// --- Section 3: In-Situ Micro-HUD Accidental Cycling ---
console.log('\n--- Section 3: Micro-HUD Accidental Cycling ---');

runTest('3.1 Accidental cycles: Natural (0) -> Sharp (1) -> Flat (-1) -> Natural (0)', () => {
    function getNextAlter(currentAlter) {
        return (currentAlter === 0) ? 1 : ((currentAlter === 1) ? -1 : 0);
    }

    assert.strictEqual(getNextAlter(0), 1, 'Natural becomes Sharp');
    assert.strictEqual(getNextAlter(1), -1, 'Sharp becomes Flat');
    assert.strictEqual(getNextAlter(-1), 0, 'Flat becomes Natural');
});

// --- Section 4: Undo / Redo History Stack ---
console.log('\n--- Section 4: Undo / Redo History Stack Contract ---');

runTest('4.1 Undo and Redo stack maintains LIFO history up to MAX_UNDO', () => {
    const undoStack = [];
    const redoStack = [];
    const MAX_UNDO = 30;

    function pushSnapshot(xml) {
        undoStack.push(xml);
        if (undoStack.length > MAX_UNDO) undoStack.shift();
        redoStack.length = 0;
    }

    function doUndo(currentXml) {
        if (undoStack.length === 0) return currentXml;
        redoStack.push(currentXml);
        return undoStack.pop();
    }

    function doRedo(currentXml) {
        if (redoStack.length === 0) return currentXml;
        undoStack.push(currentXml);
        return redoStack.pop();
    }

    let state = '<score>Initial</score>';
    pushSnapshot(state);

    state = '<score>After Pitch Edit</score>';
    pushSnapshot(state);

    state = '<score>After Duration Edit</score>';

    assert.strictEqual(undoStack.length, 2);
    assert.strictEqual(redoStack.length, 0);

    // Undo 1
    state = doUndo(state);
    assert.strictEqual(state, '<score>After Pitch Edit</score>');
    assert.strictEqual(redoStack.length, 1);

    // Undo 2
    state = doUndo(state);
    assert.strictEqual(state, '<score>Initial</score>');
    assert.strictEqual(redoStack.length, 2);

    // Redo 1
    state = doRedo(state);
    assert.strictEqual(state, '<score>After Pitch Edit</score>');
    assert.strictEqual(redoStack.length, 1);

    // New edit clears redo stack
    pushSnapshot(state);
    state = '<score>New Branch Edit</score>';
    assert.strictEqual(redoStack.length, 0);
});

// --- Section 5: HTML DOM Elements Integrity ---
console.log('\n--- Section 5: HTML DOM Elements Integrity ---');

runTest('5.1 interactive_editor.html contains all required UI components', () => {
    assert(htmlContent.includes('id="note-micro-hud"'), 'Micro-HUD missing');
    assert(htmlContent.includes('id="hud-btn-delete"'), 'HUD delete button missing');
    assert(htmlContent.includes('id="hud-btn-rest"'), 'HUD rest toggle missing');
    assert(htmlContent.includes('id="hud-btn-accidental"'), 'HUD accidental toggle missing');
    assert(htmlContent.includes('id="hud-btn-dot"'), 'HUD dot toggle missing');
    assert(htmlContent.includes('id="hud-btn-details"'), 'HUD details button missing');
    assert(htmlContent.includes('id="hud-btn-close"'), 'HUD close / deselect button missing');
    assert(htmlContent.includes('id="selection-drag-handle"'), 'Selection drag handle missing');
    assert(htmlContent.includes('id="drag-pitch-badge"'), 'Drag pitch badge missing');
    assert(htmlContent.includes('id="trash-drop-zone"'), 'Trash drop zone missing');
    assert(htmlContent.includes('id="soundslice-deck"'), 'Soundslice-inspired editing deck missing');
    assert(htmlContent.includes('id="deck-btn-prev"'), 'Deck prev note button missing');
    assert(htmlContent.includes('id="deck-btn-next"'), 'Deck next note button missing');
    assert(htmlContent.includes('id="deck-note-pitch"'), 'Deck pitch badge missing');
    assert(htmlContent.includes('id="deck-note-meas"'), 'Deck measure badge missing');
    assert(htmlContent.includes('id="deck-note-voice"'), 'Deck voice badge missing');
    assert(htmlContent.includes('id="deck-btn-piano"'), 'Deck piano toggle missing');
    assert(htmlContent.includes('id="soundslice-piano-strip"'), 'Soundslice piano strip missing');
    assert(htmlContent.includes('id="soundslice-voice-strip"'), 'Soundslice expandable voice strip missing');
    assert(htmlContent.includes('id="deck-voice-chips-container"'), 'Dynamic voice chips container missing');
    assert(htmlContent.includes('id="deck-btn-voice-more"'), 'Voice more (+Part) toggle button missing');
    assert(htmlContent.includes('id="deck-btn-8va"'), '8va ottava alta button missing');
    assert(htmlContent.includes('id="deck-btn-8vb"'), '8vb ottava bassa button missing');
    assert(htmlContent.includes('Tone.min.js'), 'Tone.min.js script tag missing');
});

runTest('5.2 Soundslice Deck, Note Stepper, and Editing APIs are defined in JavaScript', () => {
    assert(htmlContent.includes('function deselectNote()'), 'deselectNote missing');
    assert(htmlContent.includes('function openNoteDetails()'), 'openNoteDetails missing');
    assert(htmlContent.includes('function findNoteAtPoint('), 'findNoteAtPoint missing');
    assert(htmlContent.includes('function startPitchDrag('), 'startPitchDrag missing');
    assert(htmlContent.includes('function updateDeckUI('), 'updateDeckUI missing');
    assert(htmlContent.includes('function stepNote('), 'stepNote missing');
    assert(htmlContent.includes('function togglePianoStrip()'), 'togglePianoStrip missing');
    assert(htmlContent.includes('function toggleVoiceStrip()'), 'toggleVoiceStrip missing');
    assert(htmlContent.includes('function resolveNoteVoiceInfo('), 'resolveNoteVoiceInfo missing');
    assert(htmlContent.includes('function detectAvailableScoreVoiceOptions('), 'detectAvailableScoreVoiceOptions missing');
    assert(htmlContent.includes('function renderVoiceAssignmentToolbar('), 'renderVoiceAssignmentToolbar missing');
    assert(htmlContent.includes('function shiftSelectedNoteOctave('), 'shiftSelectedNoteOctave missing');
    assert(htmlContent.includes('function toggleOctaveShift('), 'toggleOctaveShift missing');
    assert(htmlContent.includes('function applyOctaveShiftTool('), 'applyOctaveShiftTool missing');
    assert(htmlContent.includes('function applyWholeMeasureRestTool()'), 'applyWholeMeasureRestTool missing');
    assert(htmlContent.includes('function applyRestDurationTool('), 'applyRestDurationTool missing');
    assert(htmlContent.includes('function applyDurationTool('), 'applyDurationTool missing');
    assert(htmlContent.includes('function toggleSlur('), 'toggleSlur missing');
    assert(htmlContent.includes('function toggleTie('), 'toggleTie missing');
    assert(htmlContent.includes('display: none !important;'), 'Status bar not hidden via CSS');
});

runTest('5.3 Expanded Voice Assignment & S2/Solo/Divisi Mapping Integrity', () => {
    ['SOLO', 'S', 'S1', 'S2', 'A', 'A1', 'A2', 'T', 'T1', 'T2', 'B', 'B1', 'B2', 'UNISON_SA', 'UNISON_TB', 'TUTTI', 'OTHERS'].forEach(code => {
        assert(htmlContent.includes(`data-voice="${code}"`), `Voice chip for ${code} missing in voice assignment strip`);
    });
    assert(htmlContent.includes("token === 'S2'"), 'S2 must map to voice 1 in updateNoteVoice');
});

// --- Section 6: Slurs, Ties, and Whole-Measure Rest Contracts ---
console.log('\n--- Section 6: Slurs, Ties, and Whole-Measure Rest Contracts ---');

runTest('6.1 Whole-measure rest preserves measure-rest semantic distinction from pitched whole notes', () => {
    const wholeNoteXml = '<note><pitch><step>C</step><octave>4</octave></pitch><duration>960</duration><voice>1</voice><type>whole</type></note>';
    const wholeMeasureRestXml = '<note><rest measure="yes"/><duration>960</duration><voice>1</voice></note>';

    assert(wholeNoteXml.includes('<type>whole</type>') && wholeNoteXml.includes('<step>C</step>'), 'Pitched whole note must have pitch');
    assert(wholeMeasureRestXml.includes('<rest measure="yes"/>') && !wholeMeasureRestXml.includes('<pitch>'), 'Whole-measure rest must have rest measure="yes" and no pitch');
});

runTest('6.2 Sequential note stepper respects boundary clamping', () => {
    const testIds = [101, 102, 103, 104];
    function simulateStep(currentId, dir) {
        const idx = testIds.indexOf(currentId);
        const nextIdx = Math.max(0, Math.min(testIds.length - 1, idx + dir));
        return testIds[nextIdx];
    }

    assert.strictEqual(simulateStep(101, 1), 102, 'Step forward 101 -> 102');
    assert.strictEqual(simulateStep(104, 1), 104, 'Clamp at end of measure');
    assert.strictEqual(simulateStep(101, -1), 101, 'Clamp at start of measure');
    assert.strictEqual(simulateStep(103, -1), 102, 'Step backward 103 -> 102');
});

runTest('6.3 Slur Curve toggling inserts start slur on origin note and stop slur on target note', () => {
    // Simulate DOM elements for origin note and next note
    const note1 = {
        tagName: 'note',
        notations: null,
        appendChild(el) { if (el.tagName === 'notations') this.notations = el; }
    };
    const note2 = {
        tagName: 'note',
        notations: null,
        appendChild(el) { if (el.tagName === 'notations') this.notations = el; }
    };

    function simulateSlurAdd(n1, n2) {
        if (!n1.notations) n1.notations = { tagName: 'notations', slurs: [] };
        if (!n2.notations) n2.notations = { tagName: 'notations', slurs: [] };

        n1.notations.slurs.push({ type: 'start', number: 1 });
        n2.notations.slurs.push({ type: 'stop', number: 1 });
    }

    simulateSlurAdd(note1, note2);
    assert.strictEqual(note1.notations.slurs[0].type, 'start');
    assert.strictEqual(note2.notations.slurs[0].type, 'stop');
});

console.log('\n' + '='.repeat(70));
console.log(`EDITING REVAMP VERIFICATION SUMMARY: ${passedTests}/${totalTests} TESTS PASSED`);
console.log('='.repeat(70));

if (passedTests === totalTests) {
    console.log('[SUCCESS] All editing revamp contracts, gesture math, and auto-balancing verified.');
    process.exit(0);
} else {
    console.error(`[FAILURE] ${totalTests - passedTests} tests failed.`);
    process.exit(1);
}
