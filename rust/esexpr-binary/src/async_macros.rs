macro_rules! do_await {
	(async, $e:expr) => {
		$e.await
	};
	(sync, $e:expr) => {
		$e
	};
}

macro_rules! pinbox_future {
	(async, $e:expr) => {
		alloc::boxed::Box::pin($e)
	};
	(sync, $e:expr) => {
		$e
	};

}

macro_rules! maybe_async {
	(async, $(#[$meta:meta])* $vis: vis fn $($func: tt)+) => {
		$(#[$meta])* $vis async fn $($func)+
	};
	(sync, $(#[$meta:meta])* $vis: vis fn $($func: tt)+) => {
		$(#[$meta])* $vis fn $($func)+
	};
}

macro_rules! if_async {
	(async, $when_async: expr, $when_sync: expr) => {
		$when_async
	};
	(sync, $when_async: expr, $when_sync: expr) => {
		$when_sync
	};
	(async, $when_async: ty, $when_sync: ty) => {
		$when_async
	};
	(sync, $when_async: ty, $when_sync: ty) => {
		$when_sync
	};
}

pub(crate) use {do_await, if_async, maybe_async, pinbox_future};
