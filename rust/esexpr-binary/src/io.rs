use alloc::vec::Vec;
use core::convert::Infallible;

/// IO `Read` type with an error parameter
pub trait Read<Error> {
	/// Read bytes into the buffer
	///
	/// # Errors
	/// Returns an error if an IO error occurs.
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
impl<R: std::io::Read> Read<std::io::Error> for R {
	fn read(&mut self, buf: &mut [u8]) -> Result<usize, std::io::Error> {
		loop {
			return match self.read(buf) {
				Ok(n) => Ok(n),
				Err(ref e) if e.kind() == std::io::ErrorKind::Interrupted => continue,
				Err(e) => Err(e),
			};
		}
	}
}

/// IO `Write` type with an error parameter
pub trait Write<Error> {
	/// Write bytes from the buffer
	///
	/// # Errors
	/// Returns an error if an IO error occurs.
	fn write(&mut self, buf: &[u8]) -> Result<(), Error>;
}

impl Write<Infallible> for Vec<u8> {
	fn write(&mut self, buf: &[u8]) -> Result<(), Infallible> {
		self.extend_from_slice(buf);
		Ok(())
	}
}

#[cfg(feature = "std")]
impl<W: std::io::Write> Write<std::io::Error> for W {
	fn write(&mut self, buf: &[u8]) -> Result<(), std::io::Error> {
		self.write_all(buf)
	}
}

/// IO `AsyncRead` type with an error parameter
#[allow(async_fn_in_trait, reason = "No additional traits to add")]
pub trait AsyncRead<Error> {
	/// Read bytes into the buffer
	///
	/// # Errors
	/// Returns an error if an IO error occurs.
	async fn read(&mut self, buf: &mut [u8]) -> Result<usize, Error>;
}

impl<'a> AsyncRead<Infallible> for &'a [u8] {
	async fn read(&mut self, buf: &mut [u8]) -> Result<usize, Infallible> {
		let len = core::cmp::min(self.len(), buf.len());
		buf.copy_from_slice(&self[..len]);
		*self = &self[len..];
		Ok(len)
	}
}

#[cfg(feature = "std")]
impl<R: futures::io::AsyncRead + std::marker::Unpin> AsyncRead<std::io::Error> for R {
	async fn read(&mut self, buf: &mut [u8]) -> Result<usize, std::io::Error> {
		<R as futures::io::AsyncReadExt>::read(self, buf).await
	}
}

/// IO `AsyncWrite` type with an error parameter
#[allow(async_fn_in_trait, reason = "No additional traits to add")]
pub trait AsyncWrite<Error> {
	/// Write bytes from the buffer
	///
	/// # Errors
	/// Returns an error if an IO error occurs.
	async fn write(&mut self, buf: &[u8]) -> Result<(), Error>;
}

impl AsyncWrite<Infallible> for Vec<u8> {
	async fn write(&mut self, buf: &[u8]) -> Result<(), Infallible> {
		self.extend_from_slice(buf);
		Ok(())
	}
}

#[cfg(feature = "std")]
impl<W: futures::io::AsyncWrite + std::marker::Unpin> AsyncWrite<std::io::Error> for W {
	async fn write(&mut self, buf: &[u8]) -> Result<(), std::io::Error> {
		<W as futures::io::AsyncWriteExt>::write_all(self, buf).await
	}
}

struct Sink;

impl Write<Infallible> for Sink {
	fn write(&mut self, _buf: &[u8]) -> Result<(), Infallible> {
		Ok(())
	}
}

impl AsyncWrite<Infallible> for Sink {
	async fn write(&mut self, _buf: &[u8]) -> Result<(), Infallible> {
		Ok(())
	}
}

/// Returns a sink that discards all data written to it.
#[must_use]
pub fn sink() -> impl Write<Infallible> + AsyncWrite<Infallible> {
	Sink
}
