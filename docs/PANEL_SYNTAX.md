# Panel syntax

Dator "features" render custom data-entry screens using a panel language
modeled on ISPF Dialog Manager panels. A panel is plain text with five
sections, each introduced by a `)NAME` header:

```
)ATTR
  @ TYPE(INPUT) LEN(20) REQUIRED(YES)
  % TYPE(OUTPUT) LEN(20)
)BODY
%------------------- CUSTOMER MAINTENANCE -------------------%
 Customer ID  . . . @CUSTID
 Name . . . . . . . @NAME
 Balance  . . . . . %BALANCE
)INIT
  &BALANCE = '0'
)PROC
  VER (&CUSTID,NONBLANK)
  VER (&NAME,NONBLANK)
  VER (&BALANCE,NUM)
)END
```

## )ATTR

Declares attribute characters: a single punctuation character (not a letter
or digit) followed by `KEYWORD(value)` pairs.

| Keyword | Values | Meaning |
|---|---|---|
| `TYPE` | `INPUT` \| `OUTPUT` | editable field, or protected/display-only field |
| `LEN` | integer | on-screen width of the field |
| `CAPS` | `ON` \| `OFF` | uppercase the entered text on save |
| `REQUIRED` | `YES` \| `NO` | reject an empty value on save, even if the column allows NULL |

## )BODY

Each line is one screen row, laid out exactly as typed. Wherever a
declared attribute character is immediately followed by an identifier
(e.g. `@CUSTID`), that identifier becomes a field at that row/column,
bound (case-insensitively) to a column of the same name on the feature's
table. Everything else on the line is literal text. A trailing `+` at the
end of a line is stripped (an ISPF continuation marker, ignored here).

Every field name in `)BODY` must match a real column on the bound table -
saving or launching a feature whose panel doesn't validates this and
reports any mismatch.

## )INIT

`&FIELD = value` (quotes optional) sets a field's default text when the
panel is opened for a brand-new row. Ignored when editing an existing row.

## )PROC

`VER (&FIELD,RULE[,args...])` checks are run, in order, before Save is
allowed to proceed:

- `VER (&FIELD,NONBLANK)` - the field must not be empty
- `VER (&FIELD,NUM)` - the field must be numeric
- `VER (&FIELD,RANGE,min,max)` - the field must be numeric and within range

## )END

Ends the panel. Anything after it is ignored.

## Notes

- Foreign-key columns (defined under a table's Relations) render as a
  combo box of referenced rows, same as the generic data-entry form.
- A field bound to a `BLOB` column is shown as a note, not editable.
- Columns not mentioned anywhere in `)BODY` are left alone: untouched on
  edit, and left to their `DEFAULT`/`NULL` on insert.
- Lines starting with `*` inside `)ATTR`, `)INIT` or `)PROC` are comments.

## Apps, features and shortcuts

An **app** (`dator_apps`) is just a named folder of **features**
(`dator_features`). Each feature binds one panel to one table and,
optionally, a shortcut code (e.g. `CUST.ADD`). Features show up under
**Apps** in the menu bar, grouped by app, and any feature with a shortcut
can be opened directly with **Apps -> Jump to Shortcut...** (Ctrl+G) -
mirroring ISPF's numbered options and `=` jump command.
