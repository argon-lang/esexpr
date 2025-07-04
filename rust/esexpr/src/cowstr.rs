//! Defines a copy-on-write type specific to str.

use core::fmt::Formatter;
use core::cmp::Ordering;
use core::hash::Hasher;
use core::ops::Deref;
use alloc::string::String;
use alloc::borrow::{Cow, ToOwned};
use crate::ValueEq;

/// A copy-on-write type for str.
/// This includes a case for static strings.
#[derive(Clone)]
pub enum CowStr<'a> {
    /// A borrowed str
    Borrowed(&'a str),

    /// A static str
    Static(&'static str),

    /// An owned string
    Owned(String)
}

impl <'a> CowStr<'a> {
    /// Gets a borrowed `CowStr`.
    pub fn as_borrowed<'b>(&'b self) -> CowStr<'b>
        where 'a: 'b
    {
        match self {
            CowStr::Borrowed(s) => CowStr::Borrowed(s),
            CowStr::Static(s) => CowStr::Static(s),
            CowStr::Owned(s) => CowStr::Borrowed(&s)
        }
    }

    /// Converts to a `CowStr` that does not borrow data.
    pub fn into_owned_cowstr(self) -> CowStr<'static> {
        match self {
            CowStr::Borrowed(s) => CowStr::Owned(s.to_owned()),
            CowStr::Static(s) => CowStr::Static(s),
            CowStr::Owned(s) => CowStr::Owned(s)
        }
    }

    /// Copies to a `CowStr` that does not borrow data.
    pub fn as_owned_cowstr(&self) -> CowStr<'static> {
        match self {
            CowStr::Borrowed(s) => CowStr::Owned((*s).to_owned()),
            CowStr::Static(s) => CowStr::Static(s),
            CowStr::Owned(s) => CowStr::Owned(s.clone())
        }
    }

    /// Convert into a `String`.
    pub fn into_string(self) -> String {
        match self {
            CowStr::Borrowed(s) | CowStr::Static(s) => s.to_owned(),
            CowStr::Owned(s) => s,
        }
    }
}

impl <'a> core::fmt::Debug for CowStr<'a> {
    fn fmt(&self, f: &mut Formatter<'_>) -> core::fmt::Result {
        self.deref().fmt(f)
    }
}

impl <'a> Deref for CowStr<'a> {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        match self {
            CowStr::Borrowed(s) => s,
            CowStr::Static(s) => s,
            CowStr::Owned(s) => s.as_str()
        }
    }
}

impl From<&'static str> for CowStr<'static> {
    fn from(value: &'static str) -> Self {
        CowStr::Static(value)
    }
}

impl From<String> for CowStr<'static> {
    fn from(value: String) -> Self {
        CowStr::Owned(value)
    }
}

impl <'a> From<CowStr<'a>> for Cow<'a, str> {
    fn from(value: CowStr<'a>) -> Self {
        match value {
            CowStr::Borrowed(s) | CowStr::Static(s) => Cow::Borrowed(s),
            CowStr::Owned(s) => Cow::Owned(s),
        }
    }
}

impl <'a, 'b> PartialEq<CowStr<'b>> for CowStr<'a> {
    fn eq(&self, other: &CowStr<'b>) -> bool {
        str::eq(self.deref(), other.deref())
    }
}

impl <'a, 'b> PartialEq<str> for CowStr<'a> {
    fn eq(&self, other: &str) -> bool {
        str::eq(self.deref(), other)
    }
}

impl <'a, 'b> PartialEq<&str> for CowStr<'a> {
    fn eq(&self, other: &&str) -> bool {
        str::eq(self.deref(), *other)
    }
}

impl <'a> PartialEq<CowStr<'a>> for str {
    fn eq(&self, other: &CowStr<'a>) -> bool {
        str::eq(self, other.deref())
    }
}

impl <'a> PartialEq<CowStr<'a>> for &str {
    fn eq(&self, other: &CowStr<'a>) -> bool {
        str::eq(self, other.deref())
    }
}

impl <'a, 'b> ValueEq<CowStr<'b>> for CowStr<'a> {
    fn value_eq(&self, other: &CowStr<'b>) -> bool {
        str::eq(self.deref(), other.deref())
    }
}

impl <'a, 'b> ValueEq<str> for CowStr<'a> {
    fn value_eq(&self, other: &str) -> bool {
        str::eq(self.deref(), other)
    }
}

impl <'a> ValueEq<CowStr<'a>> for str {
    fn value_eq(&self, other: &CowStr<'a>) -> bool {
        str::eq(self, other.deref())
    }
}

impl <'a> ValueEq<CowStr<'a>> for &str {
    fn value_eq(&self, other: &CowStr<'a>) -> bool {
        str::eq(self, other.deref())
    }
}

impl <'a> Eq for CowStr<'a> {}

impl <'a, 'b> PartialOrd<CowStr<'b>> for CowStr<'a> {
    fn partial_cmp(&self, other: &CowStr<'b>) -> Option<Ordering> {
        str::partial_cmp(self.deref(), other.deref())
    }
}

impl <'a> Ord for CowStr<'a> {
    fn cmp(&self, other: &Self) -> Ordering {
        str::cmp(self.deref(), other.deref())
    }
}

impl <'a> core::hash::Hash for CowStr<'a> {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.deref().hash(state);
    }
}

impl <'a> core::fmt::Display for CowStr<'a> {
    fn fmt(&self, f: &mut Formatter<'_>) -> core::fmt::Result {
        self.deref().fmt(f)
    }
}

impl <'a> core::borrow::Borrow<str> for CowStr<'a> {
    fn borrow(&self) -> &str {
        self.deref()
    }
}

impl <'a> core::borrow::Borrow<str> for &CowStr<'a> {
    fn borrow(&self) -> &str {
        (*self).deref()
    }
}