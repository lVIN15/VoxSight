/**
 * Test Suite: VoxSight Add Note and Add Rest Verification
 * Uses Node.js standard vm module with lightweight DOM mock
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');

console.log('======================================================================');
console.log('STARTING ADD NOTE & REST VERIFICATION SUITE');
console.log('======================================================================');

const htmlPath = path.resolve(__dirname, '../../../mobile/app/src/main/assets/interactive_editor.html');
const htmlContent = fs.readFileSync(htmlPath, 'utf8');

// 1. Verify HTML DOM markup
assert(htmlContent.includes('id="deck-btn-add-quick"'), 'deck-btn-add-quick missing from HTML');
assert(htmlContent.includes('id="deck-group-add"'), 'deck-group-add missing from HTML');
assert(htmlContent.includes('id="deck-btn-add-note"'), 'deck-btn-add-note missing from HTML');
assert(htmlContent.includes('id="deck-btn-add-rest"'), 'deck-btn-add-rest missing from HTML');
assert(htmlContent.includes('id="deck-divider-add"'), 'deck-divider-add missing from HTML');
assert(htmlContent.includes('.deck-add-btn {'), 'deck-add-btn CSS class missing from HTML');
console.log('[PASS] 1: HTML markup and CSS rules for Add group and Quick Add button present');

// 2. Extract and compile script block
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
    createElement(tag) { return new MockElement(tag); },
    addEventListener() {},
    removeEventListener() {}
};

const sandbox = {
    window: {},
    document: mockDoc,
    URLSearchParams: URLSearchParams,
    location: { search: '' },
    console: { log: () => {}, warn: () => {}, error: () => {}, debug: () => {} },
    navigator: { userAgent: 'Android Mobile', vibrate: () => {} },
    addEventListener: () => {},
    removeEventListener: () => {},
    setTimeout: (fn) => fn(),
    setInterval: () => 1,
    clearTimeout: () => {},
    clearInterval: () => {},
    AudioContext: class {},
    webkitAudioContext: class {},
    opensheetmusicdisplay: { OpenSheetMusicDisplay: class {} },
    Tone: {
        Synth: class {
            toDestination() { return this; }
            triggerAttackRelease() {}
            dispose() {}
        },
        context: { close: () => {} }
    }
};
sandbox.window = sandbox;
sandbox.window.addEventListener = () => {};
sandbox.window.removeEventListener = () => {};

vm.createContext(sandbox);
vm.runInContext(jsCode, sandbox);

assert.strictEqual(typeof sandbox.window.insertNoteAfterSelected, 'function', 'insertNoteAfterSelected should be a function');
assert.strictEqual(typeof sandbox.window.insertRestAfterSelected, 'function', 'insertRestAfterSelected should be a function');
assert.strictEqual(typeof sandbox.window.insertNoteBeforeSelected, 'function', 'insertNoteBeforeSelected should be a function');
assert.strictEqual(typeof sandbox.window.insertRestBeforeSelected, 'function', 'insertRestBeforeSelected should be a function');
console.log('[PASS] 2: JavaScript Add Note & Rest functions defined on window');

// Test preference toggling for Add group
assert.strictEqual(sandbox.window.editorPreferences.showAddGroup, true, 'showAddGroup should default to true');
console.log('[PASS] 3: editorPreferences.showAddGroup defaults to true');

sandbox.window.applyEditorPreferences({ showAddGroup: false });
const addGrp = mockDoc.getElementById('deck-group-add');
const addDiv = mockDoc.getElementById('deck-divider-add');
assert.strictEqual(addGrp.style.display, 'none', 'deck-group-add should have display: none');
assert.strictEqual(addDiv.style.display, 'none', 'deck-divider-add should have display: none');
console.log('[PASS] 4: applyEditorPreferences hides deck-group-add when showAddGroup is false');

sandbox.window.applyEditorPreferences({ showAddGroup: true });
assert.strictEqual(addGrp.style.display, '', 'deck-group-add should be visible when showAddGroup is true');
assert.strictEqual(addDiv.style.display, '', 'deck-divider-add should be visible when showAddGroup is true');
console.log('[PASS] 5: applyEditorPreferences restores deck-group-add when showAddGroup is true');

console.log('======================================================================');
console.log('ALL ADD NOTE & REST VERIFICATION TESTS PASSED (5/5)');
console.log('======================================================================');
