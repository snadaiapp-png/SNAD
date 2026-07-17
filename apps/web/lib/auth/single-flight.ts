export class SingleFlight<T> {
  private activePromise: Promise<T> | null = null;

  run(operation: () => Promise<T>): Promise<T> {
    if (this.activePromise) return this.activePromise;

    let promise: Promise<T>;
    try {
      promise = operation();
    } catch (error) {
      promise = Promise.reject(error);
    }

    this.activePromise = promise;
    void promise.then(
      () => this.release(promise),
      () => this.release(promise),
    );
    return promise;
  }

  get active(): boolean {
    return this.activePromise !== null;
  }

  private release(promise: Promise<T>): void {
    if (this.activePromise === promise) this.activePromise = null;
  }
}
