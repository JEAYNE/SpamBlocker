const core = require('@actions/core');
const github = require('@actions/github');
const fs = require('fs');


function parseTitle(title) {
	const regex = /^\[(.*?)\]\s+(.*)$/;

	const match = title.match(regex);

	const country = match[1];
	const description = match[2];

	return {
		country: country,
		description: description
	}
}
function contributing_tips() {
	return "If you'd like to share your regex or workflow here, please create an issue labeled `share regex/workflow`, see [this guide](https://github.com/aj3423/SpamBlocker/issues/249) for instructions.\n\n"
}

function generateWiki(results) {
	var wiki = {}

	for (const r of results) {
		// r: country description content link author

		if (!(r.country in wiki)) {
			wiki[r.country] = []
		}
		wiki[r.country].push(r);
	}

	let sortedCountries = Object.keys(wiki).sort();

	// Generate Markdown content
	let markdown = contributing_tips()
		+ sortedCountries.map(country => {
			let countrySection = `### ${country}\n`;
			countrySection += wiki[country].map(item => {
				const content = item.content
					.replace(/^### The regex.*?\n/, "") // drop the issue template prefix
					.trim();

				// add "    - " before each line
				// .split('\n')
				// .map(line => `    - ${line}`)
				// .join('\n');
				const title = `- [${item.description}](${item.link}) *(by @${item.author})*\n\n`
				if (content.includes("\`\`\`")) { // wrap it with ``` ``` when it's not aready wrapped
					return title + content
				} else {
					return title + `\`\`\`${content}\`\`\``
				}
			}).join('\n\n');
			return countrySection;
		}).join('\n\n');

	fs.writeFileSync('generated.md', markdown);

	// Set the output for the workflow to use
	core.setOutput('result', 'success');
}

async function run() {
	try {
		const octokit = github.getOctokit(process.env.GITHUB_TOKEN);
		const { owner, repo } = github.context.repo;

		// Fetch issues with the label "new_feature"
		const issues = await octokit.rest.issues.listForRepo({
			owner,
			repo,
			labels: 'share regex/workflow',
			state: 'all', // 'all' includes open and closed issues
			per_page: 500, // Adjust as needed for the number of issues
		});

		let results = [];

		for (const issue of issues.data) {
			// Collect title, content, link, and author
			const p = parseTitle(issue.title)
			const result = {
				country: p.country,
				description: p.description,
				content: issue.body,
				link: issue.html_url,
				author: issue.user.login
			};
			results.push(result);
		}

		generateWiki(results)

	} catch (error) {
		core.setFailed(error.message);
	}
}

(async () => {
	await run();
})();