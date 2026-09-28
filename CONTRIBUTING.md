# Contributing

Issues and pull requests are welcome.

```shell
mvn verify                       # unit tests, Flink MiniCluster test, Redis tests (need Docker)
docker compose up -d --build     # full stack
scripts/smoke-test.sh            # end-to-end check against the running stack
```

- Keep pull requests focused and add a test for new behaviour.
- Operators in the job have stable `uid`s so savepoints stay compatible. Keep them when you change
  the pipeline, and give new operators their own `uid`.
- Redis writes must stay idempotent: Flink replays records after a failure.
- Update both `README.md` and `README.zh-CN.md` when behaviour or configuration changes.
