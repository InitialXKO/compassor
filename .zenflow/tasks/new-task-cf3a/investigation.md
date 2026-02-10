# Investigation: Keyboard Enter not triggering search

## Bug Summary
When searching for a location, pressing the "Enter" key on the keyboard does not trigger the search action. The user has to manually click the search button.

## Root Cause Analysis
The current implementation of `OnEditorActionListener` only checks for `EditorInfo.IME_ACTION_SEARCH`. Some keyboards (especially hardware keyboards or specific soft keyboards) might send a `KeyEvent.KEYCODE_ENTER` instead of `IME_ACTION_SEARCH`. Additionally, some search fields (like the one in `MainActivity`'s search dialog) did not have an `OnEditorActionListener` at all.

## Affected Components
- `SearchTabFragment.kt`: Search input field in the search tab.
- `MainActivity.kt`: Search input field in the search dialog.

## Proposed Solution
Add a robust `OnEditorActionListener` to all search input fields that handles both `IME_ACTION_SEARCH` and `KeyEvent.KEYCODE_ENTER`.

## Implementation Notes
- Updated `SearchTabFragment.kt` to handle `KEYCODE_ENTER`.
- Updated `MainActivity.kt` to handle `KEYCODE_ENTER` in the search dialog and dismiss the dialog upon successful search trigger.
- Imported `KeyEvent` and `EditorInfo` to both files.
