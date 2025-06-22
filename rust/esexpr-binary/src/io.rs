use core::convert::Infallible;

use alloc::vec::Vec;

pub trait Read<Error> {
	fn read(&mut self, buf: &mut [u8]) -> Result<usize, Error>;
}

impl<'a> Read<Infallible> for &'a [u8] {
	fn read(&mut self, buf: &mut [u8]) -> Result<usize, Infallible> {
		let len = core::cmp::min(self.len(), buf.len());
		buf.copy_from_slice(&self[..len]);
		*self = &self[len..];
		Ok(len)
	}
}

#[cfg(feature = "std")]
impl <R: std::io::Read> Read<std::io::Error> for R {
	fn read(&mut self, buf: &mut [u8]) -> Result<usize, std::io::Error> {
		self.read(buf)
	}
}

pub trait Write<Error> {
	fn write(&mut self, buf: &[u8]) -> Result<usize, Error>;
	fn write_all(&mut self, buf: &[u8]) -> Result<(), Error> {
		let mut written = 0;
		while written < buf.len() {
			written += self.write(&buf[written..])?;
		}
		Ok(())
	}
}

impl Write<Infallible> for Vec<u8> {
	fn write(&mut self, buf: &[u8]) -> Result<usize, Infallible> {
		self.extend_from_slice(buf);
		Ok(buf.len())
	}
}

#[cfg(feature = "std")]
impl <W: std::io::Write> Write<std::io::Error> for W {
	fn write(&mut self, buf: &[u8]) -> Result<usize, std::io::Error> {
		self.write(buf)
	}
}


struct Sink;

impl Write<Infallible> for Sink {
	fn write(&mut self, buf: &[u8]) -> Result<usize, Infallible> {
		Ok(buf.len())
	}
}

pub fn sink() -> impl Write<Infallible> {
	Sink
}

