const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');

console.log('======================================================================');
console.log('STARTING EDITOR ADAPTIVE PREFERENCES TEST SUITE');
console.log('======================================================================');

const htmlPath = path.resolve(__dirname, '../../../mobile/app/src/main/assets/interactive_editor.html');
const htmlContent = fs.readFileSync(htmlPath, 'utf8');

// Extract script block
const scriptMatch = htmlContent.match(/<script>([\s\S]*?)<\/script>/);
assert(scriptMatch, 'Could not find <script> tag in interactive_editor.html');
const jsCode = scriptMatch[1];

// Mock DOM elements and environment
class ClassList {
    constructor() { this.classes = new Set(); }
    add(c) { this.classes.add(c); }
    remove(c) { this.classes.delete(c); }
    contains(c) { return this.classes.has(c); }
    toggle(c) { if (this.contains(c)) this.remove(c); else this.add(c); }
}

class MockElement {
    constructor(id = '') {
        this.id = id;
        this.classList = new ClassList();
        this.style = {};
        this.textContent = '';
        this.children = [];
    }
    addEventListener() {}
    removeEventListener() {}
    getBoundingClientRect() { return { left: 0, top: 0, width: 360, height: 600 }; }
    getContext() { return { clearRect: () => {}, fillRect: () => {}, strokeRect: () => {}, beginPath: () => {}, arc: () => {}, fill: () => {} }; }
    getElementsByTagName() { return []; }
}

const mockDoc = {
    body: new MockElement('body'),
    documentElement: new MockElement('html'),
    elements: new Map(),
    querySelectorAll: () => [],
    querySelector: () => null,
    getElementById(id) {
        if (!this.elements.has(id)) {
            this.elements.set(id, new MockElement(id));
        }
        return this.elements.get(id);
    },
    createElement() { return new MockElement(); },
    addEventListener() {}
};

// Create groups and dividers in mock
['deck-group-notes', 'deck-divider-notes',
 'deck-group-rests', 'deck-divider-rests',
 'deck-group-mod', 'deck-divider-mod',
 'deck-group-curves', 'deck-divider-curves',
 'deck-group-voice', 'deck-divider-voice',
 'deck-group-actions', 'soundslice-deck', 'score-container'].forEach(id => {
    mockDoc.getElementById(id);
});

let toolGroupSelectedCall = null;
const mockBridge = {
    onHistoryChanged() {},
    onPageChanged() {},
    onNoteSelected() {},
    onNoteDeselected() {},
    onToolGroupSelected(group) {
        toolGroupSelectedCall = group;
    }
};

const mockOsmdInstance = {
    load() { return Promise.resolve(); },
    render() {},
    setOptions() {},
    GraphicSheet: { Measures: [] }
};

const sandbox = {
    window: {},
    location: { search: '' },
    URLSearchParams: URLSearchParams,
    document: mockDoc,
    addEventListener: () => {},
    removeEventListener: () => {},
    navigator: { vibrate: () => {} },
    opensheetmusicdisplay: {
        OpenSheetMusicDisplay: function() { return mockOsmdInstance; }
    },
    Tone: {
        Frequency: () => ({ toFrequency: () => 440 }),
        now: () => 0
    },
    console: console,
    Math: Math,
    parseInt: parseInt,
    parseFloat: parseFloat,
    isNaN: isNaN,
    setTimeout: (fn) => fn(),
    clearTimeout: () => {},
    XMLSerializer: function() { this.serializeToString = () => ''; },
    DOMParser: function() { this.parseFromString = () => ({ getElementsByTagName: () => [] }); },
    Map: Map,
    Set: Set
};
sandbox.window = sandbox;
sandbox.window.VoxSightBridge = mockBridge;
sandbox.window.addEventListener = () => {};
sandbox.window.removeEventListener = () => {};

// Compile script
vm.createContext(sandbox);
vm.runInContext(jsCode, sandbox);

console.log('[PASS] Test 0: Script compiled inside sandbox successfully.');

// Test 1: Default Preferences state
assert(sandbox.window.editorPreferences, 'editorPreferences should be initialized');
assert.strictEqual(sandbox.window.editorPreferences.showLyrics, true);
assert.strictEqual(sandbox.window.editorPreferences.performanceProfile, 'automatic');
console.log('[PASS] 1.1: Default editorPreferences is properly initialized');

// Test 2: Apply Performance Profile
sandbox.window.applyEditorPreferences(JSON.stringify({ performanceProfile: 'performance' }));
assert(mockDoc.body.classList.contains('performance-mode'), 'body should contain performance-mode');
sandbox.window.applyEditorPreferences(JSON.stringify({ performanceProfile: 'quality' }));
assert(!mockDoc.body.classList.contains('performance-mode'), 'body should NOT contain performance-mode in quality');
console.log('[PASS] 2.1: Performance Profile toggle correctly updates document.body class');

// Test 3: Apply Density
sandbox.window.applyEditorPreferences(JSON.stringify({ toolbarDensity: 'comfortable' }));
assert(mockDoc.body.classList.contains('density-comfortable'), 'body should contain density-comfortable');
assert(!mockDoc.body.classList.contains('density-compact'), 'body should NOT contain density-compact');

sandbox.window.applyEditorPreferences(JSON.stringify({ toolbarDensity: 'compact' }));
assert(mockDoc.body.classList.contains('density-compact'), 'body should contain density-compact');
assert(!mockDoc.body.classList.contains('density-comfortable'), 'body should NOT contain density-comfortable');
console.log('[PASS] 3.1: Toolbar Density properly toggles density-comfortable and density-compact');

// Test 4: Notation display toggles
sandbox.window.applyEditorPreferences(JSON.stringify({
    showLyrics: false,
    showVoiceLabels: false,
    showMeasureNumbers: false,
    showToolLabels: false
}));
assert(mockDoc.body.classList.contains('hide-lyrics'), 'hide-lyrics should be active');
assert(mockDoc.body.classList.contains('hide-voice-labels'), 'hide-voice-labels should be active');
assert(mockDoc.body.classList.contains('hide-measure-numbers'), 'hide-measure-numbers should be active');
assert(mockDoc.body.classList.contains('hide-tool-labels'), 'hide-tool-labels should be active');

sandbox.window.applyEditorPreferences(JSON.stringify({
    showLyrics: true,
    showVoiceLabels: true,
    showMeasureNumbers: true,
    showToolLabels: true
}));
assert(!mockDoc.body.classList.contains('hide-lyrics'), 'hide-lyrics should be cleared');
assert(!mockDoc.body.classList.contains('hide-voice-labels'), 'hide-voice-labels should be cleared');
assert(!mockDoc.body.classList.contains('hide-measure-numbers'), 'hide-measure-numbers should be cleared');
assert(!mockDoc.body.classList.contains('hide-tool-labels'), 'hide-tool-labels should be cleared');
console.log('[PASS] 4.1: Notation display toggles apply and clear without re-rendering');

// Test 5: Tool group visibility
sandbox.window.applyEditorPreferences(JSON.stringify({
    showNotesGroup: false,
    showRestsGroup: true,
    showCurvesGroup: false
}));
assert.strictEqual(mockDoc.getElementById('deck-group-notes').style.display, 'none');
assert.strictEqual(mockDoc.getElementById('deck-divider-notes').style.display, 'none');
assert.strictEqual(mockDoc.getElementById('deck-group-rests').style.display, '');
assert.strictEqual(mockDoc.getElementById('deck-group-curves').style.display, 'none');
assert.strictEqual(mockDoc.getElementById('deck-divider-curves').style.display, 'none');
console.log('[PASS] 5.1: Tool group and divider visibility toggles correctly');

// Test 6: Audio Preview toggle
sandbox.window.applyEditorPreferences(JSON.stringify({ audioPreview: false }));
assert.strictEqual(sandbox.window.editorPreferences.audioPreview, false);
// playNotePreview should early return without throwing
sandbox.window.playNotePreview(60, 100);
console.log('[PASS] 6.1: Audio Preview short-circuits safely when disabled');

// Test 7: Tool group usage reporting
toolGroupSelectedCall = null;
sandbox.window.reportToolGroupUsage('rests');
assert.strictEqual(toolGroupSelectedCall, 'rests', 'Should report rests group to bridge');
assert.strictEqual(sandbox.window.editorPreferences.lastSelectedToolGroup, 'rests');
console.log('[PASS] 7.1: Tool group usage correctly dispatches to Android bridge');

// Test 8: Auto collapse deck preference
const deck = mockDoc.getElementById('soundslice-deck');
sandbox.window.applyEditorPreferences(JSON.stringify({ autoCollapseDeck: false }));
sandbox.window.updateDeckUI(null);
assert(deck.classList.contains('visible'), 'Deck should remain visible in idle state when autoCollapseDeck is false');

sandbox.window.applyEditorPreferences(JSON.stringify({ autoCollapseDeck: true }));
sandbox.window.updateDeckUI(null);
assert(!deck.classList.contains('visible'), 'Deck should collapse when autoCollapseDeck is true');
console.log('[PASS] 8.1: autoCollapseDeck preference controls deck collapse behavior');

console.log('======================================================================');
console.log('ALL PREFERENCE TESTS PASSED (8/8)');
console.log('======================================================================');
