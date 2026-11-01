# Integration of accumulated working-tree changes

Goal: review existing project changes, group coherent changes, validate and commit them to the current branch. Preserve personal files and untracked device artifacts locally; never add credentials, archives or user data. Do not bundle unrelated areas solely to make status look clean.

Scope includes existing Android, shared, desktop/native, project documentation and CI changes, subject to dependency and verification review. No release is requested. Risk: independently produced changes overlap and affect persistence, playback, external sources and security. Compare to HEAD, inspect dependencies and run appropriate tests; document failures and unverified device behavior. Keep unfinished/risky work out of commits rather than masking failure.

Sequence: inventory and baseline tests; group compatible source changes; review and commit groups with corresponding tests/docs; audit remaining status and push verified commits.
