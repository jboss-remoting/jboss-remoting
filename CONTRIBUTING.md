Contributing Guide
==================

Bug fixes and documentation improvements are welcome! If you want to contribute, I suggest you have a look at our [Jira project](https://issues.redhat.com/projects/REM3 "JBoss Remoting Jira") and get in touch with us via [Zulip chat](https://wildfly.zulipchat.com/#narrow/stream/173893-remoting "#remoting").


PRs must be submitted to 5.1 branch and they should:
- state clearly what they do (see more [here](https://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html))
- point to associated [Jira](https://issues.redhat.com/browse/REM3) issue
- contain a test case, unless existing tests already verify the code added by the PR
- have a license header in all new files, with current year’s number
- pass CI

If your PR is incomplete, the reviewer might request you add the missing bits or add them for you if that is simple enough (for
that to be possible, though, you need to check the [Allow edits from maintainers](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/working-with-forks/allowing-changes-to-a-pull-request-branch-created-from-a-fork) box)

We expect all contributors and users to follow our [Code of Conduct](CODE_OF_CONDUCT.md) when communicating through project channels. These include, but are not limited to: chat, issues, code.

# Issues

JBoss Remoting uses JIRA to manage issues. All issues can be found [here](https://issues.redhat.com/projects/REM3/issues).

To create a new issue, comment on an existing issue, or assign an issue to yourself, you'll need to first [create a JIRA account](https://issues.redhat.com/).

## Good First Issues

Want to contribute to JBoss Remoting but aren't quite sure where to start? Check out our issues with the `good-first-issue` label. These are a triaged set of issues that are great for getting started on our project. These can be found [here](https://issues.redhat.com/issues/?jql=project%20%3D%20REM3%20%20and%20labels%20in%20(%22good-first-issue%22)).

Once you have selected an issue you would like to work on, make sure it's not already assigned to someone else. To assign an issue to yourself, simply click on "Start Progress". This will automatically assign the issue to you.

## Discussing your Planned Changes

If you want feedback, you can discuss your planned changes in any of the following ways: 
* add comments to the issue ticket at the [JBoss Remoting Jira](https://issues.redhat.com/browse/REM3)
* Remoting [Zulip chat](https://wildfly.zulipchat.com/#narrow/stream/173893-remoting "#remoting").
* or simply create a draft PR and point in the PR description that you would like feedback on the proposal before getting to the
final solution


PR Review Process
-----------------

PR reviewers will take into account the following aspects when reviewing your PR:
- correctness: the code must be correct
- performance impact: if there are negative performance impacts in the code, careful consideration must be taken whereas the impact could be eliminated and, in case it cannot, if the new code should be accepted
- code style: keep your code style consistent with the classes you are editing, such as variable names, ordering of methods, etc
- scope of the fix: this is a very important factor. Sometimes, the fix should be applied to a broader range of classes, such as a bug that repeats itself in other parts of the code. Other times, the PR solves a bug only partially, because the bug has a broader impact than initially evaluated.
- is the proposed fix the best approach for the Jira at hand?
- backwards compatibility: we must prevent any PR that breaks compatibility with previous versions. If the PR does so, it could still be okay, but this should be clearly documented it will probably be discussed by the project maintainers before being merged
- security impact: it is critical to evaluate if the PR has any sort of security impact, preventing the addition of exploitable flaws.


GitHub Quickstart
-----------------

If this is your first time contributing to a GitHub project, you can follow the next steps to get up to speed when contributing to
JBoss Remoting. Regardless of your level of experience, though, we kindly ask you that PRs are always rebased before being submitted or
updated.

## One time setup

### Create a GitHub account

If you don't have one already, head to https://github.com/

### Fork JBoss Remoting

Fork https://github.com/jboss-remoting/jboss-remoting into your GitHub account.

### Clone your newly forked repository onto your local machine

```bash
git clone git@github.com:[your username]/jboss-remoting.git
cd jboss-remoting
```

### Add a remote reference to upstream

This makes it easy to pull down changes in the project over time

```bash
git remote add upstream git://github.com/jboss-remoting/jboss-remoting.git
```

## Development Process

This is the typical process you would follow to submit any changes to JBoss Remoting.

### Pulling updates from upstream

```bash
git pull --rebase upstream 5.1
```

> Note that --rebase will automatically move your local commits, if you have
> any, on top of the latest branch you pull from.
> If you don't have any commits it is safe to leave off, but for safety it
> doesn't hurt to use it each time just in case you have a commit you've
> forgotten about!

### Create a simple topic branch to isolate your work (recommended)

```bash
git checkout -b my_cool_feature
```

If you have a Jira number for the fix, having the Jira name in the branch is very useful to keep track of your changes:

```bash
git checkout -b REM3-XXXX
```
or
```bash
git checkout -b REM3-XXXX-my_cool_feature
```


### Make the changes

Make whatever code changes, including new tests to verify your change, and make sure the project builds without errors:

```bash
mvn clean verify
```

> If you're making non code changes, the above step is not required.

### Commit changes

Add whichever files were changed into 'staging' before performing a commit:

```bash
git commit -v
```
The `-v` parameter is advisable if you want to check when writing the commit message all the changes that are included in your
commit.

### Rebase changes against 5.1

Once all your commits for the issue have been made against your local topic branch, we need to rebase it against branch 5.1 in upstream to ensure that your commits are added on top of the current state of 5.1. This will make it easier to incorporate your changes into the 5.1 branch, especially if there has been any significant time passed since you rebased at the beginning.

```bash
git pull --rebase upstream 5.1
```

### Push to your repo

Now that you've sync'd your topic branch with 5.1, it's time to push it to your GitHub repo.

```bash
git push origin REM3-XXXX-my_cool_feature
```

### Getting your changes merged into upstream, a pull request

Now your updates are in your GitHub repo, you will need to notify the project that you have code/docs for inclusion.

* Send a pull request, by clicking the pull request link while in your repository fork
* After review a maintainer will merge your pull request, update/resolve associated issues, and reply when complete
* Lastly, switch back to branch 5.1 from your topic branch and pull the updates

```bash
git checkout 5.1
git pull upstream 5.1
```

* You may also choose to update your origin on GitHub as well

```bash
git push origin
```

#### Updating a PR

After you get feedback from reviewers and the community, you might need to update your PR before it is merged.
If the original commit is too big, you can do an incremental, new commit, to facilitate review of the changes in the PR.

However, if you want to edit your previous commit, you can easily do so by amending it:


```bash
git commit --amend -v
```

Don't forget to add the changes you want incorporated to your commit before amending it.

If your PR contains more than one commit and you need to edit a commit that is not the latest, it cannot be amended as above,
but you can use the interactive magic rebase. The command below allows you to amend, merge, delete or simply reword the latest
X commits in the current branch (replace X by the correct number for your case):

```bash
git rebase -i HEAD~X
```

Then just follow the instructions to indicate the changes you need to do.

It is a good practice to create a backup of your original branch in case you end up doing a mistake. That way, you can just
reload your original fix (the GitHub remote origin account containing the PR can serve this purpose, as long as you don't
overwrite it with a broken branch).

Once you are satisfied if your commits, run the tests again with `mvn clean verify`. Finally, check the changes your are going
to push to origin are really okay with:

```bash
git log -p
```

Only then, you can push it to origin. If you edited a commit that was already in the PR, you will need to force the push with `--force`:

```bash
git push origin --force REM3-XXXX-my_cool_feature
```